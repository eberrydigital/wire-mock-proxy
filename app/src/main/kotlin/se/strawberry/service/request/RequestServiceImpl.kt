package se.strawberry.service.request

import com.fasterxml.jackson.databind.ObjectMapper
import se.strawberry.api.models.traffic.HttRequestModel
import se.strawberry.api.models.traffic.HttpResponseModel
import se.strawberry.api.models.traffic.RecordedTrafficInstanceModel
import se.strawberry.common.Paths.ADMIN_PREFIX
import se.strawberry.common.Paths.API_PREFIX
import se.strawberry.common.Paths.UI_ASSETS_PREFIX
import se.strawberry.common.Paths.UI_ROOT
import se.strawberry.repository.traffic.RecordedRequestRepository
import se.strawberry.repository.traffic.RecordedRequestRepository.RecordedRequest

class RequestServiceImpl(
    private val mapper: ObjectMapper,
    private val repository: RecordedRequestRepository
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

        // If sessionId is provided, use optimized query
        val source = if (sessionId != null) {
            repository.listBySession(sessionId, limit * 2) // Fetch a bit more to accommodate filtering
        } else {
            // If no session ID, we technically can't list easily with current Repo contract (it requires sessionId).
            // But existing WireMockClient.listServeEvents() returned ALL.
            // Requirement GAP: "Global traffic view" vs "Session view".
            // The UI usually queries with sessionId.
            // If sessionId is missing, we return empty list or need a 'global' scan.
            // Let's assume for now we only support listing within a session or return nothing if missing
            // to enforce session usage. Or we return empty list.
            // However, debugging might need global view.
            return emptyList()
        }

        return source.asSequence()
            .sortedByDescending { it.timestamp }
            .filter { req -> showInternal || !shouldBeHiddenFromUI(req.path) }
            .filter { method == null || it.method.equals(method, true) }
            .filter { pathSub == null || it.path.contains(pathSub, ignoreCase = true) }
            .filter { statusFilter == null || it.responseStatus == statusFilter }
            .take(limit)
            .map { toModel(it, includeBodies = false) }
            .toList()
    }

    override fun byId(id: String): RecordedTrafficInstanceModel? {
        val req = repository.get(id) ?: return null
        if (shouldBeHiddenFromUI(req.path)) return null
        return toModel(req, includeBodies = true)
    }

    override fun clear() {
        // Clear all is dangerous in DDB but method exists.
        repository.clearAll()
    }

    override fun exportAsNdjson(): String {
        // Not easily supported without full scan or session context.
        // Returning empty or implementing session-based export if query has session.
        // For now, simple empty impl or todo.
        return ""
    }

    private fun toModel(req: RecordedRequest, includeBodies: Boolean = false): RecordedTrafficInstanceModel {
        fun bodyPretty(body: String?, headers: Map<String, String>): String? {
            if (!includeBodies || body == null) return null
            val contentType = headers.entries.find { it.key.equals("Content-Type", true) }?.value
            
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

        val reqContentType = req.headers.entries.find { it.key.equals("Content-Type", true) }?.value
        val resContentType = req.responseHeaders.entries.find { it.key.equals("Content-Type", true) }?.value

        return RecordedTrafficInstanceModel(
            id = req.id,
            receivedAt = req.timestamp,
            timingMs = req.duration.toInt(),
            request = HttRequestModel(
                method = req.method,
                url = req.path,
                headers = maskHeaders(req.headers),
                body = bodyPretty(req.body, req.headers),
                contentType = reqContentType
            ),
            response = HttpResponseModel(
                status = req.responseStatus,
                headers = maskHeaders(req.responseHeaders),
                body = bodyPretty(req.responseBody, req.responseHeaders),
                contentType = resContentType
            )
        )
    }

    private fun shouldBeHiddenFromUI(url: String): Boolean {
        if (url == UI_ROOT) return true
        if (url.startsWith(UI_ASSETS_PREFIX)) return true
        if (url.startsWith(API_PREFIX)) return true
        if (url.startsWith(ADMIN_PREFIX)) return true
        return false
    }

    private fun maskHeaders(h: Map<String, String>): Map<String, String> =
        h.mapValues { (k, v) ->
            when (k.lowercase()) {
                "authorization", "cookie", "set-cookie", "x-api-key" -> "•••masked•••"
                else -> v
            }
        }
}




