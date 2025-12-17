package helpers

import se.strawberry.repository.stub.StubRepository

class FakeStubRepository : StubRepository {
    val stubs = mutableListOf<StubRepository.Stub>()

    override fun save(stub: StubRepository.Stub) {
        stubs.removeIf { it.stubId == stub.stubId }
        stubs.add(stub)
    }

    override fun get(sessionId: String, stubId: String): StubRepository.Stub? {
        return stubs.find { it.sessionId == sessionId && it.stubId == stubId }
    }

    override fun listBySession(sessionId: String): List<StubRepository.Stub> {
        return stubs.filter { it.sessionId == sessionId }
    }

    override fun delete(sessionId: String, stubId: String) {
        stubs.removeIf { it.sessionId == sessionId && it.stubId == stubId }
    }

    override fun findByStubId(stubId: String): StubRepository.Stub? {
        return stubs.find { it.stubId == stubId }
    }

    override fun getAllActive(): List<StubRepository.Stub> {
        return stubs.filter { it.status == StubRepository.Stub.Status.ACTIVE }
    }
}

class FakeSessionRepository : se.strawberry.repository.session.SessionRepository {
    val sessions = mutableMapOf<String, se.strawberry.repository.session.SessionRepository.Session>()

    override fun create(session: se.strawberry.repository.session.SessionRepository.Session): Boolean {
        sessions[session.id] = session
        return true
    }

    override fun get(id: String): se.strawberry.repository.session.SessionRepository.Session? {
        return sessions[id]
    }

    override fun close(id: String): Boolean {
        val s = sessions[id] ?: return false
        sessions[id] = s.copy(status = se.strawberry.repository.session.SessionRepository.Session.Status.CLOSED)
        return true
    }
}
