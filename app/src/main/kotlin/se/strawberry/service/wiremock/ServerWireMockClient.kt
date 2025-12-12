package se.strawberry.service.wiremock

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import se.strawberry.admin.ServerRef

/**
 * R1.4: Adapter around the global WireMock ServerRef to satisfy WireMockClient boundary.
 */
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
}

