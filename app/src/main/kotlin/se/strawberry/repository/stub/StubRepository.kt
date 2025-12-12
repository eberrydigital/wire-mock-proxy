package se.strawberry.repository.stub

/**
 * Repository boundary for stubs persisted in DB.
 * R1.3: interface only; implementations to arrive in R5.
 */
interface StubRepository {
    data class Stub(
        val id: String,
        val sessionId: String?,
        val name: String?,
        val description: String?,
        val requestMatcherJson: String, // JSON representation
        val responseSpecJson: String,   // JSON representation
        val wiremockMappingId: String?,
        val enabled: Boolean = true,
        val createdAt: Long,
        val updatedAt: Long,
    )

    fun create(stub: Stub): Boolean
    fun update(stub: Stub): Boolean
    fun get(id: String): Stub?
    fun listBySession(sessionId: String): List<Stub>
    fun enable(id: String): Boolean
    fun disable(id: String): Boolean
    fun delete(id: String): Boolean
}

