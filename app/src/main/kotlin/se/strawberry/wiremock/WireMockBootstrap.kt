package se.strawberry.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.slf4j.LoggerFactory
import se.strawberry.common.Headers.X_MOCK_TARGET_SERVICE
import se.strawberry.common.Priorities.PROXY_FALLBACK

class WireMockBootstrap(
    private val server: WireMockServer
) {
    private val log = LoggerFactory.getLogger(WireMockBootstrap::class.java)

    fun initFallbackProxy() {
        // Catch-all fallback: proxy destination is taken from header via your response-template helper
        server.stubFor(
            any(urlMatching(".*"))
                .atPriority(PROXY_FALLBACK)
                .willReturn(
                    aResponse()
                        .proxiedFrom("{{service-origin name=request.headers.[$X_MOCK_TARGET_SERVICE]}}")
                        .withTransformers("response-template")
                )
        )

        log.info("WireMock fallback proxy mapping installed (priority={})", PROXY_FALLBACK)
    }
}
