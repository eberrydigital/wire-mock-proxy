package tests

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import se.strawberry.app.mockGateway
import se.strawberry.admin.ServerRef
import se.strawberry.app.DependenciesKey
import se.strawberry.app.buildDependencies
import se.strawberry.app.installDependencies
import se.strawberry.common.Json
import se.strawberry.domain.stub.*
import se.strawberry.stubs.dto.StubBuilder
import kotlin.random.Random

class TestKtorParity : BaseTest() {

    @Test
    fun health_should_return_ok() = testApplication {
        application {
            attributes.put(DependenciesKey, buildDependencies())
            installDependencies()
            mockGateway()
        }
        val resp = client.get("/_proxy-api/health")
        assertThat(resp.status, equalTo(HttpStatusCode.OK))
        assertThat(resp.bodyAsText(), containsString("\"ok\""))
    }

    @Test
    fun stubs_list_should_reflect_server_state() = testApplication {
        application {
            attributes.put(DependenciesKey, buildDependencies())
            installDependencies()
            mockGateway()
        }
        // Arrange: add a stub directly to ServerRef
        val dto = CreateStubRequest(
            request = ReqMatch(
                method = ReqMatchMethods.GET,
                url = UrlMatch(UrlMatchType.EXACT, "/api/parity"),
                headers = emptyMap(),
                body = null
            ),
            response = RespDef(
                mode = RespMode.STATIC,
                status = 200,
                headers = mapOf("Content-Type" to "text/plain"),
                bodyText = "ok",
                bodyJson = null,
                patch = null
            )
        )
        val stub = StubBuilder.buildStubMapping(dto)
        ServerRef.server.addStubMapping(stub)

        val resp = client.get("/_proxy-api/stubs")
        assertThat(resp.status, equalTo(HttpStatusCode.OK))
        val body = resp.bodyAsText()
        assertThat(body, containsString(stub.id.toString()))
    }

    @Test
    fun requests_list_should_match_recorded_events() = testApplication {
        application {
            attributes.put(DependenciesKey, buildDependencies())
            installDependencies()
            mockGateway()
        }
        val endpoint = "/api/parity"
        val sessionId = Random.nextInt().toString()

        // Upstream behavior
        upstream.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(endpoint)
            ).willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(200).withBody("upstream-ok")
            )
        )

        // Generate a request through proxy
        call(sessionId, endpoint).close()

        val resp = client.get("/_proxy-api/requests")
        assertThat(resp.status, equalTo(HttpStatusCode.OK))
        val body = resp.bodyAsText()
        // Basic parity: contains the endpoint and status
        assertThat(body, containsString(endpoint))
        assertThat(body, containsString("\"status\":200"))
    }

    @Test
    fun create_stub_via_ktor_should_mirror_wiremock_api_behavior() = testApplication {
        application {
            attributes.put(DependenciesKey, buildDependencies())
            installDependencies()
            mockGateway()
        }
        val endpoint = "/api/ktor-create"
        val sessionId = Random.nextInt().toString()

        val dto = CreateStubRequest(
            request = ReqMatch(
                method = ReqMatchMethods.GET,
                url = UrlMatch(UrlMatchType.EXACT, endpoint),
                headers = emptyMap(),
                body = null
            ),
            response = RespDef(
                mode = RespMode.STATIC,
                status = 200,
                headers = mapOf("Content-Type" to "text/plain"),
                bodyText = "stubbed",
                bodyJson = null,
                patch = null
            ),
            ephemeral = Ephemeral(uses = 2)
        )

        val json = Json.mapper.writeValueAsString(dto)
        val resp = client.post("/_proxy-api/stubs") {
            contentType(ContentType.Application.Json)
            header("X-Mock-Session-Id", sessionId)
            setBody(json)
        }
        assertThat(resp.status, equalTo(HttpStatusCode.Created))
        val payload = resp.bodyAsText()
        assertThat(payload, containsString("summary"))

        // Call twice to consume ephemeral uses
        call(sessionId, endpoint).close()
        call(sessionId, endpoint).close()
        // Third call should hit upstream (no stub)
        upstream.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(endpoint)
            ).willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(200).withBody("upstream-ok")
            )
        )
        val third = call(sessionId, endpoint)
        assertThat(third.code, equalTo(200))
        third.close()
    }
}

