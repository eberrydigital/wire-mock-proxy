package tests

import api.ProxyApi
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import okhttp3.Request
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import stubs.Stubs

class TestStubbingOnlyWorksWithinTheSameSession : ProxyTestBase() {
    @Test
    fun test() {
        val UPSTREAM_DEFAULT_STATUS_CODE = 201
        val UPSTREAM_DEFAULT_BODY = "upstream-default-response"
        val UPSTREAM_TEST_ENDPOINT = "/api/test"
        val STUB_RESPONSE_CODE = 200

        // We're just configuring here what upstream should return, it can be anything
        upstream.stubFor(get(urlEqualTo(UPSTREAM_TEST_ENDPOINT)).willReturn(aResponse().withStatus(UPSTREAM_DEFAULT_STATUS_CODE)))

        val sessionId = "some_session_id"
        val url = "${proxyBaseUrl()}$UPSTREAM_TEST_ENDPOINT"

        val stubCreationResponse = ProxyApi.createStub(
            client = http,
            proxyBaseUrl = proxyBaseUrl(),
            targetService = upstreamServiceName,
            stubJsonBody =  Stubs.getExact(UPSTREAM_TEST_ENDPOINT, status = STUB_RESPONSE_CODE, bodyText = UPSTREAM_DEFAULT_BODY, uses = 1000),
            sessionId = sessionId
        )

        assertThat("Stub creation should be successful", stubCreationResponse.code, equalTo(201))

        fun call(sessionId: String?): okhttp3.Response {
            val req = Request.Builder()
                .url(url)
                .addHeader("X-Mock-Target-Service", upstreamServiceName)
                .apply { if (sessionId != null) addHeader("X-Mock-Session-Id", sessionId) }
                .build()
            return http.newCall(req)
                .execute()
        }

        val responseWithPassedSessionId = call(sessionId)
        assertThat(responseWithPassedSessionId.code, equalTo(STUB_RESPONSE_CODE))
        assertThat(responseWithPassedSessionId.body.string(), equalTo(UPSTREAM_DEFAULT_BODY))

        val anotherCallWithPassedSessionId = call(sessionId)
        assertThat(anotherCallWithPassedSessionId.code, equalTo(STUB_RESPONSE_CODE))
        assertThat(anotherCallWithPassedSessionId.body.string(), equalTo(UPSTREAM_DEFAULT_BODY))

        val responseWithUnknownSessionId = call("unknown_session_id")
        assertThat(responseWithUnknownSessionId.code, equalTo(UPSTREAM_DEFAULT_STATUS_CODE)) // Match should not happen so we go to upstream
    }
}