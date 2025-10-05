package se.strawberry.api.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import se.strawberry.admin.ServerRef
import se.strawberry.common.Headers
import se.strawberry.common.Paths
import se.strawberry.common.Paths.ADMIN_PREFIX
import se.strawberry.common.Paths.API_PREFIX
import se.strawberry.common.Paths.UI_ASSETS_PREFIX
import se.strawberry.common.Paths.UI_ROOT
import se.strawberry.config.UiBlacklist.DEVTOOLS_WELL_KNOWN
import se.strawberry.config.UiBlacklist.FAVICON
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class RequestsHandler(
    private val mapper: ObjectMapper
) {
    fun list(query: Map<String, String>): Response {
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
            .filter { ev -> showInternal || !shouldBeHiddenFromUI(ev.request.url) }
            .filter { method == null || it.request.method.value().equals(method, true) }
            .filter { pathSub == null || it.request.url.contains(pathSub, ignoreCase = true) }
            .filter { statusFilter == null || it.response.status == statusFilter }
            .take(limit)
            .map { toDto(it) }
            .toList()

        val json = mapper.writeValueAsString(events)
        return json(200, json)
    }

    fun byId(id: String): Response {
        val ev = ServerRef.server.allServeEvents.find { it.id.toString() == id }
            ?: return json(404, """{"error":"not_found"}""")

        if (shouldBeHiddenFromUI(ev.request.url)) {
            return json(404, """{"error":"not_found"}""")
        }

        val json = mapper.writeValueAsString(toDto(ev, includeBodies = true))
        return json(200, json)
    }

    fun clear(): Response {
        ServerRef.server.resetRequests()
        return Response.response().status(204).build()
    }

    fun export(): Response {
        val sb = StringBuilder()
        ServerRef.server.allServeEvents
            .sortedBy { it.request.loggedDate }
            .forEach {
                sb.append(mapper.writeValueAsString(toDto(it, includeBodies = true))).append('\n')
            }

        return Response.response()
            .status(200)
            .headers(
                HttpHeaders(
                    HttpHeader.httpHeader("Content-Type", "application/x-ndjson"),
                    HttpHeader.httpHeader("Content-Disposition", "attachment; filename=\"requests.jsonl\"")
                )
            )
            .body(sb.toString())
            .build()
    }


    private fun toDto(ev: ServeEvent, includeBodies: Boolean = false): Map<String, Any?> {
        val req = ev.request
        val res = ev.response

        fun bodyPretty(body: String?, contentType: String?): String? {
            if (!includeBodies || body == null) return null
            if (contentType?.contains("application/json", true) == true) {
                return try {
                    val tree = mapper.readTree(body)
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree)
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
                    req.headers?.keys().orEmpty().associateWith { k -> req.getHeader(k) }
                ),
                "body" to bodyPretty(req.bodyAsString?.takeIf { it.isNotEmpty() }, reqContentType),
                "contentType" to reqContentType
            ),
            "response" to mapOf(
                "status" to res.status,
                "headers" to maskHeaders(
                    res.headers?.keys().orEmpty().associateWith { k -> headerValue(res.headers, k) }
                ),
                "body" to bodyPretty(res.bodyAsString?.takeIf { it.isNotEmpty() }, resContentType),
                "contentType" to resContentType
            )
        )
    }

    fun splitPathAndQuery(raw: String): Pair<String, Map<String, String>> {
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
        headers?.getHeader(name)?.takeIf { it.isPresent }?.firstValue()

    private fun requestHeaderValue(req: Request, name: String): String? =
        req.headers?.getHeader(name)?.takeIf { it.isPresent }?.firstValue()

    private fun shouldBeHiddenFromUI(url: String): Boolean {
        if (url == FAVICON) return true
        if (url == UI_ROOT) return true
        if (url.startsWith(DEVTOOLS_WELL_KNOWN)) return true
        if (url.startsWith(UI_ASSETS_PREFIX)) return true
        if (url.startsWith(API_PREFIX)) return true
        if (url.startsWith(ADMIN_PREFIX)) return true

        return false
    }

    private fun maskHeaders(h: Map<String, String?>): Map<String, String?> =
        h.mapValues { (k, v) ->
            when (k.lowercase()) {
                "authorization", "cookie", "set-cookie", "x-api-key" -> v?.let { "•••masked•••" }
                else -> v
            }
        }

    private fun json(code: Int, body: String): Response =
        Response.response()
            .status(code)
            .headers(HttpHeaders(HttpHeader.httpHeader(Headers.CONTENT_TYPE, Headers.JSON)))
            .body(body)
            .build()
}
