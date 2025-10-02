package se.strawberry.transform

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import se.strawberry.admin.ServerRef
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class RequestsApiTransformer : ResponseTransformerV2 {

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder()
        .build()
    )

    override fun getName(): String = "requests-api"

    override fun applyGlobally(): Boolean = false

    override fun transform(response: Response, serveEvent: ServeEvent): Response {
        val req = serveEvent.request

        EnvironmentConfig.adminApiToken?.let { token ->
            val header = requestHeaderValue(req, "Authorization")
            if (header == null || header != "Bearer $token") {
                return Response.response()
                    .status(401)
                    .headers(jsonHeaders())
                    .body("""{"error":"unauthorized"}""")
                    .build()
            }
        }

        val url = req.url // for example: "/_proxy-api/requests?method=GET"
        if (!url.startsWith("/_proxy-api")) {
            // Just in case
            return response
        }
        val (route, query) = splitPathAndQuery(url.removePrefix("/_proxy-api"))

        return when {
            route == "/requests" -> handleList(query)
            route == "/requests/clear" && req.method.value() == "POST" -> handleClear()
            route == "/export" -> handleExport()
            route.startsWith("/requests/") -> {
                val id = route.removePrefix("/requests/")
                    .trim('/')
                handleById(id)
            }

            else -> Response.response()
                .status(404)
                .headers(jsonHeaders())
                .body("""{"error":"not_found"}""")
                .build()
        }
    }

    // ---------- Handlers ----------

    private fun handleList(query: Map<String, String>): Response {
        val method = query["method"]?.uppercase()
        val pathSub = query["path"]
        val statusFilter = query["status"]?.toIntOrNull()
        val limit = query["limit"]?.toIntOrNull() ?: 200
        val showInternal = when (query["internal"]?.lowercase()) {
            "1", "true", "yes", "y" -> true
            else -> false
        }

        val all = ServerRef.server.allServeEvents

        val events = all.asSequence()
            .sortedByDescending { it.request.loggedDate }
            .filter { ev -> showInternal || !isInternalRequestUrl(ev.request.url) }
            .filter { method == null || it.request.method.value().equals(method, true) }
            .filter { pathSub == null || it.request.url.contains(pathSub, ignoreCase = true) }
            .filter { statusFilter == null || it.response.status == statusFilter }
            .take(limit)
            .map { toDto(it) }
            .toList()

        val json = mapper.writeValueAsString(events)
        return Response.response().status(200).headers(jsonHeaders()).body(json).build()
    }

    private fun handleById(id: String): Response {
        val ev = ServerRef.server.allServeEvents.find { it.id.toString() == id }
            ?: return Response.response().status(404).headers(jsonHeaders()).body("""{"error":"not_found"}""").build()

        if (isInternalRequestUrl(ev.request.url)) {
            return Response.response().status(404).headers(jsonHeaders()).body("""{"error":"not_found"}""").build()
        }

        val json = mapper.writeValueAsString(toDto(ev, includeBodies = true))
        return Response.response().status(200).headers(jsonHeaders()).body(json).build()
    }

    private fun handleClear(): Response {
        ServerRef.server.resetRequests()
        return Response.response()
            .status(204)
            .build()
    }

    private fun handleExport(): Response {
        val sb = StringBuilder()
        ServerRef.server.allServeEvents
            .sortedBy { it.request.loggedDate }
            .forEach {
                sb.append(mapper.writeValueAsString(toDto(it, includeBodies = true)))
                    .append('\n')
            }
        return Response.response()
            .status(200)
            .headers(HttpHeaders(
                HttpHeader.httpHeader("Content-Type", "application/x-ndjson"),
                HttpHeader.httpHeader("Content-Disposition", "attachment; filename=\"requests.jsonl\"")
            )
            )
            .body(sb.toString())
            .build()
    }

    // ---------- Helpers ----------

    private fun toDto(ev: ServeEvent, includeBodies: Boolean = false): Map<String, Any?> {
        val req = ev.request
        val res = ev.response

        fun bodyPretty(body: String?, contentType: String?): String? {
            if (!includeBodies || body == null) return null
            if (contentType?.contains("application/json", true) == true) {
                return try {
                    val tree = mapper.readTree(body)
                    mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(tree)
                } catch (_: Exception) {
                    body
                }
            }
            return body
        }

        val reqContentType = requestHeaderValue(req, "Content-Type")
        val resContentType = headerValue(res.headers, "Content-Type")

        return mapOf(
            "id" to ev.id.toString(),
            "receivedAt" to req.loggedDate,
            "timingMs" to ev.timing?.totalTime,
            "request" to mapOf(
                "method" to req.method.value(),
                "url" to req.url,
                "headers" to maskHeaders(
                    req.headers?.keys()
                        .orEmpty()
                        .associateWith { k ->
                            // getHeader(k) не кидает, возвращает null/строку
                            req.getHeader(k)
                        }
                ),
                "body" to bodyPretty(
                    // у WireMock bodyAsString безопасен, но может вернуть "" — норм
                    req.bodyAsString?.takeIf { it.isNotEmpty() },
                    reqContentType
                ),
                "contentType" to reqContentType
            ),
            "response" to mapOf(
                "status" to res.status,
                "headers" to maskHeaders(
                    res.headers?.keys()
                        .orEmpty()
                        .associateWith { k ->
                            headerValue(res.headers, k)
                        }
                ),
                "body" to bodyPretty(
                    res.bodyAsString?.takeIf { it.isNotEmpty() },
                    resContentType
                ),
                "contentType" to resContentType
            )
        )
    }

    private fun jsonHeaders() = HttpHeaders(HttpHeader.httpHeader("Content-Type", "application/json"))

    private fun maskHeaders(h: Map<String, String?>): Map<String, String?> =
        h.mapValues { (k, v) ->
            when (k.lowercase()) {
                "authorization", "cookie", "set-cookie", "x-api-key" -> v?.let { "•••masked•••" }
                else -> v
            }
        }

    private fun splitPathAndQuery(raw: String): Pair<String, Map<String, String>> {
        val parts = raw.split("?", limit = 2)
        val path = parts[0].ifEmpty { "/" }
        val q = if (parts.size == 2) {
            parts[1].split("&")
                .filter { it.contains("=") }
                .associate {
                    val (k, v) = it.split("=", limit = 2)
                    URLDecoder.decode(k, StandardCharsets.UTF_8) to URLDecoder.decode(v, StandardCharsets.UTF_8)
                }
        } else emptyMap()
        return path to q
    }

    private fun headerValue(headers: HttpHeaders?, name: String): String? =
        headers?.getHeader(name)
            ?.takeIf { it.isPresent }
            ?.firstValue()

    private fun requestHeaderValue(req: Request, name: String): String? =
        req.headers?.getHeader(name)
            ?.takeIf { it.isPresent }
            ?.firstValue()

    private fun isInternalRequestUrl(url: String): Boolean =
        url.startsWith("/_proxy-api") ||
                url == "/_proxy-ui" ||
                url.startsWith("/__admin")
}