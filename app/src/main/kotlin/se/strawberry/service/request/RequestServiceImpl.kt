package se.strawberry.service.request

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import se.strawberry.api.models.traffic.HttRequestModel
import se.strawberry.api.models.traffic.HttpResponseModel
import se.strawberry.api.models.traffic.RecordedTrafficInstanceModel
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

    override fun list(query: Map<String, String>): List<RecordedTrafficInstanceModel> {
        val method = query["method"]?.uppercase()
        val pathSub = query["path"]
        val statusFilter = query["status"]?.toIntOrNull()
        val limit = query["limit"]?.toIntOrNull() ?: 200
        val showInternal = when (query["internal"]?.lowercase()) {
            "1", "true", "yes", "y" -> true
            else -> false
        }
        val sessionId = query["sessionId"]?.trim()?.takeIf { it.isNotEmpty() }

        return wireMockClient.listServeEvents().asSequence()
            .sortedByDescending { it.request.loggedDate }
            .filter { event -> showInternal || !shouldBeHiddenFromUI(event.request.url) }
            .filter { method == null || it.request.method.value().equals(method, true) }
            .filter { pathSub == null || it.request.url.contains(pathSub, ignoreCase = true) }
            .filter { statusFilter == null || it.response.status == statusFilter }
            .filter { sessionId == null || it.request.getHeader(Headers.X_MOCK_SESSION_ID) == sessionId }
            .take(limit)
            .map { toModel(it, includeBodies = false) }
            .toList()
    }

    override fun byId(id: String): RecordedTrafficInstanceModel? {
        val ev = wireMockClient.findServeEvent(id) ?: return null
        if (shouldBeHiddenFromUI(ev.request.url)) return null
        return toModel(ev, includeBodies = true)
    }

    override fun clear() {
        wireMockClient.resetRequests()
    }

    override fun exportAsNdjson(): String {
        val sb = StringBuilder()
        wireMockClient.listServeEvents()
            .sortedBy { it.request.loggedDate }
            .forEach {
                sb.append(mapper.writeValueAsString(toModel(it, includeBodies = true))).append('\n')
            }
        return sb.toString()
    }

    private fun toModel(ev: ServeEvent, includeBodies: Boolean = false): RecordedTrafficInstanceModel {
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
        val resContentType = res.headers?.getHeader("Content-Type")?.takeIf { it.isPresent }?.firstValue()

        return RecordedTrafficInstanceModel(
            id = ev.id.toString(),
            receivedAt = req.loggedDate.time,
            timingMs = ev.timing?.totalTime,
            request = HttRequestModel(
                method = req.method.value(),
                url = req.url,
                headers = maskHeaders(
                    req.headers?.keys().orEmpty().associateWith { k -> req.getHeader(k) }
                ),
                body = bodyPretty(req.bodyAsString?.takeIf { it.isNotEmpty() }, reqContentType),
                contentType = reqContentType
            ),
            response = HttpResponseModel(
                status = res.status,
                headers = maskHeaders(
                    res.headers?.keys().orEmpty().associateWith { k ->
                        res.headers.getHeader(k)?.takeIf { it.isPresent }?.firstValue()
                    }
                ),
                body = bodyPretty(res.bodyAsString?.takeIf { it.isNotEmpty() }, resContentType),
                contentType = resContentType
            )
        )
    }

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
}




