package se.strawberry.repository.traffic

/**
 * Repository boundary for recorded requests.
 */
interface RecordedRequestRepository {
    data class RecordedRequest(
        val id: String,
        val sessionId: String,
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: String?,
        val responseStatus: Int,
        val responseHeaders: Map<String, String>,
        val responseBody: String?,
        val timestamp: Long,
        val duration: Long,
        val stubbed: Boolean
    )

    fun save(rr: RecordedRequest): Boolean
    fun listBySession(sessionId: String, limit: Int = 200): List<RecordedRequest>
    fun get(id: String): RecordedRequest?
    fun clearAll(): Int
}

