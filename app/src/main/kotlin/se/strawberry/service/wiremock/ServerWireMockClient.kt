package se.strawberry.service.wiremock

import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import se.strawberry.admin.ServerRef


class ServerWireMockClient : WireMockClient {
    override fun addStub(stub: StubMapping) {
        ServerRef.server.addStubMapping(stub)
    }

    override fun removeStub(stub: StubMapping) {
        ServerRef.server.removeStubMapping(stub)
    }

    override fun listStubs(): List<StubMapping> = ServerRef.server.listAllStubMappings().mappings.toList()

    override fun resetRequests() {
        ServerRef.server.resetRequests()
    }

    override fun listServeEvents(): List<ServeEvent> {
        return ServerRef.server.allServeEvents
    }

    override fun findServeEvent(id: String): ServeEvent? {
        return ServerRef.server.allServeEvents.find { it.id.toString() == id }
    }
}

