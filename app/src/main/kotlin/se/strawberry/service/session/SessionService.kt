package se.strawberry.service.session


interface SessionService {
    /** Resolve/validate a sessionId; may generate or fetch metadata later. */
    fun normalize(sessionId: String?): String?

    /** Close a session (no-op for now). */
    fun close(sessionId: String): Boolean
}

