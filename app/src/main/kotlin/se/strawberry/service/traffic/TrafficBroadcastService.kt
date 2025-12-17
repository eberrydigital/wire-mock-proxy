package se.strawberry.service.traffic

import se.strawberry.repository.traffic.RecordedRequestRepository.RecordedRequest

interface TrafficBroadcastService {
    suspend fun broadcast(request: RecordedRequest)
    fun addSession(id: String, send: suspend (String) -> Unit)
    fun removeSession(id: String)
    fun updateFilter(id: String, sessionId: String?)
}
