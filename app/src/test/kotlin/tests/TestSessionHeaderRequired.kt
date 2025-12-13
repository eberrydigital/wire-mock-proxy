package tests

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import tests.setup.BaseTest

class TestSessionHeaderRequired : BaseTest() {

    @Test
    fun shouldRejectWhenSessionHeaderMissing() {
        val endpoint = "/api/test"
        val upstreamStatus = 200
        upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(
                    aResponse()
                        .withStatus(upstreamStatus)
                        .withBody("ok")
                )
        )

        call(sessionId = null, endpoint = endpoint).use { resp ->
            assertThat(resp.code, equalTo(400))
        }
    }
}

