package se.strawberry.service.request

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import se.strawberry.common.Headers
import se.strawberry.common.Paths.ADMIN_PREFIX
import se.strawberry.common.Paths.API_PREFIX
import se.strawberry.common.Paths.UI_ASSETS_PREFIX
import se.strawberry.common.Paths.UI_ROOT
import se.strawberry.service.wiremock.WireMockClient

class RequestServiceImpl(
    private val mapper: ObjectMapper,
    private val wireMockClient: WireMockClient
) : RequestService {

    override fun list(query: Map<String, String>): Response {
        val method = query["method"]?.uppercase()
        val pathSub = query["path"]
        val statusFilter = query["status"]?.toIntOrNull()
        val limit = query["limit"]?.toIntOrNull() ?: 200
        val showInternal = when (query["internal"]?.lowercase()) {
            "1", "true", "yes", "y" -> true
            else -> false
        }
        val sessionId = query["sessionId"]?.trim()?.takeIf { it.isNotEmpty() }

        val events = wireMockClient.listServeEvents().asSequence()
            .sortedByDescending { it.request.loggedDate }
            .filter { event -> showInternal || !shouldBeHiddenFromUI(event.request.url) }
            .filter { method == null || it.request.method.value().equals(method, true) }
            .filter { pathSub == null || it.request.url.contains(pathSub, ignoreCase = true) }
            .filter { statusFilter == null || it.response.status == statusFilter }
            .filter { sessionId == null || it.request.getHeader(Headers.X_MOCK_SESSION_ID) == sessionId }
            .take(limit)
            .map { toDto(it) }
            .toList()

        return json(200, mapper.writeValueAsString(events))
    }

    override fun byId(id: String): Response {
        val ev = wireMockClient.findServeEvent(id)
            ?: return json(404, """{"error":"not_found"}""")

        if (shouldBeHiddenFromUI(ev.request.url)) {
            return json(404, """{"error":"not_found"}""")
        }

        val json = mapper.writeValueAsString(toDto(ev, includeBodies = true))
        return json(200, json)
    }

    override fun clear(): Response {
        wireMockClient.resetRequests()
        return Response.response().status(204).build()
    }

    override fun export(): Response {
        val sb = StringBuilder()
        wireMockClient.listServeEvents()
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

    private fun headerValue(headers: HttpHeaders?, name: String): String? =
        headers?.getHeader(name)?.takeIf { it.isPresent }?.firstValue()

    private fun requestHeaderValue(req: Request, name: String): String? =
        req.headers?.getHeader(name)?.takeIf { it.isPresent }?.firstValue()

    private fun shouldBeHiddenFromUI(url: String): Boolean {
        if (url == UI_ROOT) return true
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




