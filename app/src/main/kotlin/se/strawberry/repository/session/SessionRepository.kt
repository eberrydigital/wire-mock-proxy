package se.strawberry.repository.session

/**
 * Repository boundary for sessions.
 */
interface SessionRepository {
    data class Session(
        val id: String,
        val name: String? = null,
        val owner: String? = null,
        val createdAt: Long,
        val expiresAt: Long?,
        val status: Status = Status.ACTIVE,
    ) {
        enum class Status { ACTIVE, CLOSED, EXPIRED }
    }

    fun create(session: Session): Boolean
    fun get(id: String): Session?
    fun close(id: String): Boolean
}

