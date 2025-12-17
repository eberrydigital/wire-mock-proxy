package se.strawberry.service.traffic

import com.fasterxml.jackson.databind.ObjectMapper
import se.strawberry.repository.traffic.RecordedRequestRepository.RecordedRequest
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

class WebSocketTrafficBroadcaster(
    private val mapper: ObjectMapper
) : TrafficBroadcastService {
    
    private val log = LoggerFactory.getLogger(javaClass)
    
    // map of connectionId -> sender function
    private val sessions = ConcurrentHashMap<String, suspend (String) -> Unit>()
    // map of connectionId -> filter sessionId (null means all)
    private val filters = ConcurrentHashMap<String, String>()

    override fun addSession(id: String, send: suspend (String) -> Unit) {
        sessions[id] = send
        log.info("WebSocket connected: $id")
    }

    override fun removeSession(id: String) {
        sessions.remove(id)
        filters.remove(id)
        log.info("WebSocket disconnected: $id")
    }

    override fun updateFilter(id: String, sessionId: String?) {
        if (sessionId != null) {
            filters[id] = sessionId
            log.info("WebSocket $id filtering for session $sessionId")
        } else {
            filters.remove(id)
            log.info("WebSocket $id cleared filter")
        }
    }

    override suspend fun broadcast(request: RecordedRequest) {
        val json = try {
            mapper.writeValueAsString(request)
        } catch (e: Exception) {
            log.error("Failed to serialize traffic for broadcast", e)
            return
        }

        sessions.forEach { (id, send) ->
            val filter = filters[id]
            if (filter == null || filter == request.sessionId) {
                try {
                    send(json)
                } catch (e: Exception) {
                    log.warn("Failed to send to WebSocket $id", e)
                }
            }
        }
    }
}
