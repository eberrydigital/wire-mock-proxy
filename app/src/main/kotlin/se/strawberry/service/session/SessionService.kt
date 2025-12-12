package se.strawberry.service.session

/**
 * Service boundary for session lifecycle and lookup.
 * R1.2: interface only; persistence arrives in R2.
 */
interface SessionService {
    /** Resolve/validate a sessionId; may generate or fetch metadata later. */
    fun normalize(sessionId: String?): String?

    /** Close a session (no-op for now). */
    fun close(sessionId: String): Boolean
}

