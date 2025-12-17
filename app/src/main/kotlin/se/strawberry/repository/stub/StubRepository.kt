package se.strawberry.repository.stub

/**
 * Repository boundary for stubs persisted in DB.
 */
interface StubRepository {
    data class Stub(
        val sessionId: String, // PK
        val stubId: String,    // SK
        val mappingJson: String,
        val createdAt: Long,
        val updatedAt: Long,
        val expiresAt: Long?,
        val usesLeft: Int?,
        val status: Status
    ) {
        enum class Status { ACTIVE, EXHAUSTED, EXPIRED }
    }

    fun save(stub: Stub)
    fun get(sessionId: String, stubId: String): Stub?
    fun listBySession(sessionId: String): List<Stub>
    fun delete(sessionId: String, stubId: String)
    /** Look up a stub by its global ID using GSI. Useful for deletion without session context. */
    fun findByStubId(stubId: String): Stub?
    fun getAllActive(): List<Stub> // For startup sync (might need scan or GSI)
}

