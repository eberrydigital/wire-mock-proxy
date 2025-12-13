package tests.unit

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.domain.stub.*
import se.strawberry.service.stub.StubServiceImpl
import se.strawberry.service.wiremock.WireMockClient

class StubServiceImplTest {

    private val mapper: ObjectMapper = Json.mapper

    @Test
    fun `create should add session-scoped stub and return created payload`() {
        val client = FakeWireMockClient()
        val service = StubServiceImpl(mapper, client)

        val sessionId = "s-123"
        val dto = CreateStubRequest(
            request = ReqMatch(
                method = ReqMatchMethods.GET,
                url = UrlMatch(UrlMatchType.EXACT, "/api/test"),
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
            ephemeral = Ephemeral(uses = 2, ttlMs = 10_000)
        )

        val resp = service.create(dto, sessionId)

        assertThat(resp.status, equalTo(201))
        assertThat(resp.headers.getHeader(Headers.CONTENT_TYPE).firstValue(), containsString("application/json"))

        // Verify stub was added
        assertThat(client.stubs.size, equalTo(1))
        val added = client.stubs.single()

        // Verify session header matcher is present inside mapping (serialize to json for a stable assertion)
        val mappingJson = mapper.writeValueAsString(added)
        assertThat(mappingJson, containsString(Headers.X_MOCK_SESSION_ID))
        assertThat(mappingJson, containsString(sessionId))

        // Verify response payload
        val payload = resp.bodyAsString
        assertThat(payload, containsString("\"id\""))
        assertThat(payload, containsString("\"summary\""))
        assertThat(payload, containsString("\"usesLeft\":2"))
        assertThat(payload, containsString("\"expiresAt\""))
    }

    @Test
    fun `list should return all stubs as json`() {
        val client = FakeWireMockClient()
        val service = StubServiceImpl(mapper, client)

        // Arrange: add two stubs
        client.addStub(se.strawberry.stubs.dto.StubBuilder.buildStubMapping(sampleDto("/a")))
        client.addStub(se.strawberry.stubs.dto.StubBuilder.buildStubMapping(sampleDto("/b")))

        val resp = service.list()

        assertThat(resp.status, equalTo(200))
        val body = resp.bodyAsString
        assertThat(body, startsWith("["))
        assertThat(body, containsString("/a"))
        assertThat(body, containsString("/b"))
    }

    @Test
    fun `delete should remove existing stub and return 204`() {
        val client = FakeWireMockClient()
        val service = StubServiceImpl(mapper, client)

        val stub = se.strawberry.stubs.dto.StubBuilder.buildStubMapping(sampleDto("/x"))
        client.addStub(stub)

        val resp = service.delete(stub.id.toString())

        assertThat(resp.status, equalTo(204))
        assertThat(client.stubs, empty())
    }

    @Test
    fun `delete should return 404 when stub not found`() {
        val client = FakeWireMockClient()
        val service = StubServiceImpl(mapper, client)

        val resp = service.delete("does-not-exist")

        assertThat(resp.status, equalTo(404))
        assertThat(resp.bodyAsString, containsString("not_found"))
    }

    private fun sampleDto(path: String): CreateStubRequest =
        CreateStubRequest(
            request = ReqMatch(
                method = ReqMatchMethods.GET,
                url = UrlMatch(UrlMatchType.EXACT, path),
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
            ),
            ephemeral = Ephemeral(uses = 1, ttlMs = null)
        )

    private class FakeWireMockClient : WireMockClient {
        val stubs = mutableListOf<StubMapping>()

        override fun addStub(stub: StubMapping) {
            stubs.add(stub)
        }

        override fun removeStub(stub: StubMapping) {
            stubs.removeIf { it.id == stub.id }
        }

        override fun listStubs(): List<StubMapping> = stubs.toList()

        override fun resetRequests() {
            // not needed for stub service tests
        }

        override fun listServeEvents(): List<ServeEvent> = emptyList()
        override fun findServeEvent(id: String): ServeEvent? = null
    }
}
