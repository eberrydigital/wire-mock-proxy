package se.strawberry.repository.session

import java.util.concurrent.ConcurrentHashMap

/**
 * R2.1: In-memory implementation for SessionRepository to unblock development before DB wiring.
 */
class InMemorySessionRepository : SessionRepository {
    private val store = ConcurrentHashMap<String, SessionRepository.Session>()

    override fun create(session: SessionRepository.Session): Boolean {
      store[session.id] = session
      return true
    }

    override fun get(id: String): SessionRepository.Session? = store[id]

    override fun close(id: String): Boolean {
        val current = store[id] ?: return false
        store[id] = current.copy(status = SessionRepository.Session.Status.CLOSED)
        return true
    }
}

