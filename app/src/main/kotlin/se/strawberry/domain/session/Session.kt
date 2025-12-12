package se.strawberry.domain.session

/**
 * Domain entity for session lifecycle.
 */
data class Session(
    val id: String,
    val name: String? = null,
    val owner: String? = null,
    val createdAt: Long,
    val expiresAt: Long?,
    val status: Status = Status.ACTIVE,
) {
    enum class Status { ACTIVE, CLOSED }
}

