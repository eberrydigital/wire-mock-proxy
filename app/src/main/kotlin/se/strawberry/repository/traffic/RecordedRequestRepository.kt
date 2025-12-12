package se.strawberry.repository.traffic

/**
 * Repository boundary for recorded requests.
 * R1.3: interface only; implementation will arrive in R4.
 */
interface RecordedRequestRepository {
    data class RecordedRequest(
        val id: String,
        val sessionId: String?,
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: String?,
        val responseStatus: Int,
        val responseHeaders: Map<String, String>,
        val responseBody: String?,
        val timestamp: Long,
    )

    fun save(rr: RecordedRequest): Boolean
    fun listBySession(sessionId: String, limit: Int = 200): List<RecordedRequest>
    fun get(id: String): RecordedRequest?
    fun clearAll(): Int
}

