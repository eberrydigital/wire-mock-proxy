package helpers

import se.strawberry.repository.stub.StubRepository
import com.github.tomakehurst.wiremock.stubbing.ServeEvent

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
