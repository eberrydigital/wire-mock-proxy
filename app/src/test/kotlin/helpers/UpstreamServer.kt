package helpers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import fixtures.TestPorts

/**
 * Helper class that imitates an upstream server using WireMock.
 * This can be used in integration tests to simulate external services.
 * It gives us a predictable and controllable service to test against.
 */

class UpstreamServer {
    lateinit var wireMock: WireMockServer

    companion object {
        const val UPSTREAM_DEFAULT_CODE = 301
        const val UPSTREAM_ENDPOINT = "/upstream/test"
        const val UPSTREAM_SERVICE_NAME = "upstream-service-name"
        val upstreamDefaultBody: String =  jacksonObjectMapper().writeValueAsString(UpstreamResponseBody())
        val UPSTREAM_PORT: Int = TestPorts.allocate()
    }

    /**
     * Define mock API endpoints and responses here
     */
    private fun mockApis() {
        wireMock.stubFor(
            get(urlEqualTo(UPSTREAM_ENDPOINT))
                .willReturn(aResponse()
                    .withStatus(UPSTREAM_DEFAULT_CODE)
                    .withBody(upstreamDefaultBody)
                )
        )
    }

    private fun setUpWireMock() {
        wireMock = WireMockServer(
            options()
                .port(UPSTREAM_PORT)
                .disableRequestJournal()
        )
        mockApis()
    }

    fun start() {
        setUpWireMock()
        wireMock.start()
    }

    fun stop() {
        wireMock.stop()
    }

    data class UpstreamResponseBody(
        val message: String = "This is the default response from the upstream server"
    )
}