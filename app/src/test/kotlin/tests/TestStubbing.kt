package tests

import helpers.ProxyApi
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import se.strawberry.domain.stub.Ephemeral
import stubs.Stubs
import tests.setup.BaseTest
import kotlin.random.Random

class TestStubbingOnlyWorksWithinTheSameSession : BaseTest() {

    @Test
    fun test() {
        val upstreamStatus = 201
        val upstreamBody = "upstream-default-response"
        val stubBody = "stub-response"
        val endpoint = "/api/test"
        val stubStatus = 200
        val sessionId = Random.hashCode().toString()

        upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(aResponse()
                    .withStatus(upstreamStatus)
                    .withBody(upstreamBody)
                )
        )

        val stub = Stubs.createStubRequest(
            url = endpoint,
            status = stubStatus,
            bodyText = stubBody,
            ephemeral = Ephemeral(uses = 3)
        )

        val createResp = ProxyApi.createStub(
            client = http,
            apiBaseUrl = apiBaseUrl(),
            targetService = upstreamServiceName,
            stub = stub,
            sessionId = sessionId
        )

        assertThat("Stub creation should be successful (${createResp.code})",
            createResp.code in listOf(200, 201), equalTo(true))
        createResp.close()

        call(sessionId, endpoint).use { responseForCalWithKnownSession ->
            assertThat(responseForCalWithKnownSession.code, equalTo(stubStatus))
            assertThat(responseForCalWithKnownSession.body.string(), equalTo(stubBody))
        }
        call(sessionId, endpoint).use { responseForCalWithKnownSession ->
            assertThat(responseForCalWithKnownSession.code, equalTo(stubStatus))
            assertThat(responseForCalWithKnownSession.body.string(), equalTo(stubBody))
        }
        call("unknown", endpoint).use { responseWithUnknownSession ->
            assertThat(responseWithUnknownSession.code, equalTo(upstreamStatus))
            assertThat(responseWithUnknownSession.body.string(), equalTo(upstreamBody))
        }
    }
}
