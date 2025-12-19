package se.strawberry.service.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.stubbing.StubMapping


class ServerWireMockClient(private val server: WireMockServer)  : WireMockClient {
    override fun addStub(stub: StubMapping) {
        server.addStubMapping(stub)
    }

    override fun removeStub(stub: StubMapping) {
        server.removeStubMapping(stub)
    }

    override fun listStubs(): List<StubMapping> = server.listAllStubMappings().mappings.toList()

    override fun resetRequests() {
        server.resetRequests()
    }

    override fun listServeEvents(): List<ServeEvent> {
        return server.allServeEvents
    }

    override fun findServeEvent(id: String): ServeEvent? {
        return server.allServeEvents.find { it.id.toString() == id }
    }
}

