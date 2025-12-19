package se.strawberry.service.wiremock

import com.github.tomakehurst.wiremock.stubbing.StubMapping

class WireMockAdminImpl(
    private val client: Lazy<WireMockClient>
) : WireMockAdmin {
    private val clientInstance get() = client.value
    override fun removeStub(mapping: StubMapping) = clientInstance.removeStub(mapping)
    override fun editStub(mapping: StubMapping) = clientInstance.editStub(mapping)
}
