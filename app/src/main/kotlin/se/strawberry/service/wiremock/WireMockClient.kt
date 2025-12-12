package se.strawberry.service.wiremock

import com.github.tomakehurst.wiremock.stubbing.StubMapping

/**
 * Thin boundary over WireMock operations used by services/handlers.
 * R1.2: interface only; implementation will adapt ServerRef in R1.4.
 */
interface WireMockClient {
    fun addStub(stub: StubMapping)
    fun removeStub(stub: StubMapping)
    fun listStubs(): List<StubMapping>
    fun resetRequests()
}

