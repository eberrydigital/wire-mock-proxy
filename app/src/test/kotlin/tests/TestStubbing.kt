package tests

import api.ProxyApi
import com.github.tomakehurst.wiremock.client.WireMock.*
import okhttp3.Request
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import stubs.Stubs

class TestStubbingOnlyWorksWithinTheSameSession : BaseTest() {

    @Test
    fun test() {
        val upstreamStatus = 201
        val upstreamBody = "upstream-default-response"
        val endpoint = "/api/test"
        val stubStatus = 200
        val sessionId = "A-123"

        upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(aResponse()
                    .withStatus(upstreamStatus)
                    .withBody(upstreamBody)
                )
        )

        val stub = Stubs.getExactStaticText(
            url = endpoint,
            status = stubStatus,
            bodyText = upstreamBody,
            uses = 3
        )

        val createResp = ProxyApi.createStub(
            client = http,
            proxyBaseUrl = proxyBaseUrl(),
            targetService = upstreamServiceName,
            stub = stub,
            sessionId = sessionId
        )
        assertThat("Stub creation should be successful (${createResp.code})",
            createResp.code in listOf(200, 201), equalTo(true))
        createResp.close()

        fun call(sessionId: String): okhttp3.Response {
            val req = Request.Builder()
                .url("${proxyBaseUrl()}$endpoint")
                .addHeader("X-Mock-Target-Service", upstreamServiceName)
                .addHeader("X-Mock-Session-Id", sessionId)
                .build()
            return http.newCall(req).execute()
        }

        call(sessionId).use { responseForCalWithKnownSession ->
            assertThat(responseForCalWithKnownSession.code, equalTo(stubStatus))
            assertThat(responseForCalWithKnownSession.body.string(), equalTo(upstreamBody))
        }
        call(sessionId).use { responseForCalWithKnownSession ->
            assertThat(responseForCalWithKnownSession.code, equalTo(stubStatus))
            assertThat(responseForCalWithKnownSession.body.string(), equalTo(upstreamBody))
        }
        call("unknown").use { responseWithUnknownSession ->
            assertThat(responseWithUnknownSession.code, equalTo(upstreamStatus))
            assertThat(responseWithUnknownSession.body.string(), equalTo(upstreamBody))
        }
    }
}
