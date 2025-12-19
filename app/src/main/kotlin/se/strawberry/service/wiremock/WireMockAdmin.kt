package se.strawberry.service.wiremock

import com.github.tomakehurst.wiremock.stubbing.StubMapping

interface WireMockAdmin {
    fun removeStub(mapping: StubMapping)
    fun editStub(mapping: StubMapping)
}