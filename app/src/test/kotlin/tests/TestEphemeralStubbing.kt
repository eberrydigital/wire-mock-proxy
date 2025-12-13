package tests

import annotations.DecisionTableId
import helpers.ProxyApi
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import se.strawberry.domain.stub.Ephemeral
import stubs.Stubs
import tests.setup.BaseTest
import kotlin.random.Random

class TestEphemeralStubbing : BaseTest() {
    val upstreamStatus = 201
    val upstreamBody = "upstream-default-response"
    val stubBody = "stub-response"
    val endpoint = "/api/test"
    val stubStatus = 200
    val sessionId = "A-123"

    @Test
    @DecisionTableId("EPH_1")
    fun shouldIgnoreTtlWhenUsesIsSetAndTtlIsNull() {
        val ttl = null
        val uses = 3

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
            ephemeral = Ephemeral(uses = uses, ttlMs = ttl)
        )

        ProxyApi.createStub(
            client = http,
            apiBaseUrl = apiBaseUrl(),
            targetService = upstreamServiceName,
            stub = stub,
            sessionId = sessionId
        ).close()

        repeat(uses) {
            call(sessionId, endpoint).use { response ->
                assertThat(response.code, equalTo(stubStatus))
                assertThat(response.body.string(), equalTo(stubBody))
            }
        }

        call(sessionId, endpoint).use { response ->
            assertThat(response.code, equalTo(upstreamStatus))
            assertThat(response.body.string(), equalTo(upstreamBody))
        }
    }


    @Test
    @DecisionTableId("EPH_2")
    fun shouldApplyWhenBothSet() {
        val ttl = 5000L
        val uses = 5

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
            ephemeral = Ephemeral(uses = uses, ttlMs = ttl)
        )

        ProxyApi.createStub(
            client = http,
            apiBaseUrl = apiBaseUrl(),
            targetService = upstreamServiceName,
            stub = stub,
            sessionId = sessionId
        ).close()

        repeat(uses) {
            call(sessionId, endpoint).use { response ->
                assertThat(response.code, equalTo(stubStatus))
                assertThat(response.body.string(), equalTo(stubBody))
            }
        }
    }

    @Test
    @DecisionTableId("EPH_3")
    fun shouldStopMatchingWhenNoUsesLeftWhenBothSet() {
        val ttl = 5000L
        val uses = 3

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
            ephemeral = Ephemeral(uses = uses, ttlMs = ttl)
        )

       ProxyApi.createStub(
            client = http,
            apiBaseUrl = apiBaseUrl(),
            targetService = upstreamServiceName,
            stub = stub,
            sessionId = sessionId
        ).close()

        repeat(uses) {
            call(sessionId, endpoint).use { response ->
                assertThat(response.code, equalTo(stubStatus))
                assertThat(response.body.string(), equalTo(stubBody))
            }
        }

        call(sessionId, endpoint).use { response ->
            assertThat(response.code, equalTo(upstreamStatus))
            assertThat(response.body.string(), equalTo(upstreamBody))
        }
    }

    @Test
    @DecisionTableId("EPH_4")
    fun shouldStopMatchingWhenTtlExpiresWhenBothSet() {
        val ttl = 2000L
        val uses = 5

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
            ephemeral = Ephemeral(uses = uses, ttlMs = ttl)
        )

        ProxyApi.createStub(
            client = http,
            apiBaseUrl = apiBaseUrl(),
            targetService = upstreamServiceName,
            stub = stub,
            sessionId = sessionId
        ).close()

        val callsBeforeExpiry = Random.nextInt(1, uses)
        repeat(callsBeforeExpiry) {
            call(sessionId, endpoint).use { response ->
                assertThat(response.code, equalTo(stubStatus))
                assertThat(response.body.string(), equalTo(stubBody))
            }
        }

        Thread.sleep(ttl)

        call(sessionId, endpoint).use { response ->
            assertThat(response.code, equalTo(upstreamStatus))
            assertThat(response.body.string(), equalTo(upstreamBody))
        }
    }

    @Test
    @DecisionTableId("EPH_5")
    fun shouldIgnoreUsesWhenTtlIsSetAndUsesAreNull() {
        val ttl = 2000L
        val uses = null

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
            ephemeral = Ephemeral(uses = uses, ttlMs = ttl)
        )

      ProxyApi.createStub(
            client = http,
            apiBaseUrl = apiBaseUrl(),
            targetService = upstreamServiceName,
            stub = stub,
            sessionId = sessionId
        ).close()

        repeat(Random.nextInt(1, 5)) {
            call(sessionId, endpoint).use { response ->
                assertThat(response.code, equalTo(stubStatus))
                assertThat(response.body.string(), equalTo(stubBody))
            }
        }

        Thread.sleep(ttl)

        call(sessionId, endpoint).use { response ->
            assertThat(response.code, equalTo(upstreamStatus))
            assertThat(response.body.string(), equalTo(upstreamBody))
        }
    }
}
