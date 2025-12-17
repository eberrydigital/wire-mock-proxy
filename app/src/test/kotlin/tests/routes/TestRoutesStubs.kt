package tests.routes

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Response
import helpers.DependencyHelper.buildFakeDependency
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import se.strawberry.api.DependenciesKey
import se.strawberry.api.mockGateway
import se.strawberry.api.models.stub.CreateStubRequest
import se.strawberry.api.models.stub.Ephemeral
import se.strawberry.api.models.stub.ReqMatch
import se.strawberry.api.models.stub.ReqMatchMethods
import se.strawberry.api.models.stub.RespDef
import se.strawberry.api.models.stub.RespMode
import se.strawberry.api.models.stub.UrlMatch
import se.strawberry.api.models.stub.UrlMatchType
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.service.stub.StubService

class StubsRoutesTest {

    private val mapper: ObjectMapper = Json.mapper

    @Test
    fun `POST _proxy-api_stubs - missing session header returns 400`() = testApplication {
        // Arrange
        val fakeStubService = RecordingStubService(mapper)
        application {
            attributes.put(DependenciesKey, buildFakeDependency().copy(mapper = mapper, stubService = fakeStubService))
            mockGateway()
        }

        // Act
        val resp = client.post("/_proxy-api/stubs") {
            contentType(ContentType.Application.Json)
            setBody(validCreateStubJson())
            // no session header
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("missing_session"))
        assertEquals(0, fakeStubService.createCalls.size)
    }

    @Test
    fun `POST _proxy-api_stubs - valid request returns 201 and calls StubService`() = testApplication {
        // Arrange
        val fakeStubService = RecordingStubService(mapper)
        application {
            attributes.put(DependenciesKey, buildFakeDependency().copy(mapper = mapper, stubService = fakeStubService))
            mockGateway()
        }

        // Act
        val resp = client.post("/_proxy-api/stubs") {
            contentType(ContentType.Application.Json)
            header(Headers.X_MOCK_SESSION_ID, "s-123")
            setBody(validCreateStubJson())
        }

        // Assert: status
        assertEquals(HttpStatusCode.Created, resp.status)

        // Assert: response json from stub service
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"id\""))
        assertTrue(body.contains("\"summary\""))

        // Assert: service called with expected session
        assertEquals(1, fakeStubService.createCalls.size)
        assertEquals("s-123", fakeStubService.createCalls.single().sessionId)
        assertEquals("/ping", fakeStubService.createCalls.single().dto.request.url.value)
    }

    @Test
    fun `GET _proxy-api_stubs - returns 200 and list payload`() = testApplication {
        // Arrange
        val fakeStubService = RecordingStubService(mapper).apply {
            listResponseBody = """[{"id":"1"},{"id":"2"}]"""
        }
        application {
            attributes.put(DependenciesKey, buildFakeDependency().copy(mapper = mapper, stubService = fakeStubService))
            mockGateway()
        }

        // Act
        val resp = client.get("/_proxy-api/stubs")

        // Assert
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals("""[{"id":"1"},{"id":"2"}]""", resp.bodyAsText())
        assertEquals(1, fakeStubService.listCalls)
    }

    @Test
    fun `POST _proxy-api_stubs - invalid json returns 400`() = testApplication {
        val fakeStubService = RecordingStubService(mapper)
        application {
            attributes.put(DependenciesKey, buildFakeDependency().copy(mapper = mapper, stubService = fakeStubService))
            mockGateway()
        }

        val resp = client.post("/_proxy-api/stubs") {
            contentType(ContentType.Application.Json)
            header(Headers.X_MOCK_SESSION_ID, "s-1")
            setBody("{not-valid-json}")
        }

        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertTrue(resp.bodyAsText().contains("invalid_json"))
        assertEquals(0, fakeStubService.createCalls.size)
    }

    @Test
    fun `DELETE _proxy-api_stubs - missing id returns 400`() = testApplication {
        val fakeStubService = RecordingStubService(mapper)
        application {
            attributes.put(DependenciesKey, buildFakeDependency().copy(mapper = mapper, stubService = fakeStubService))
            mockGateway()
        }

        val resp = client.delete("/_proxy-api/stubs/")

        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `DELETE _proxy-api_stubs - valid id returns 204 and calls service`() = testApplication {
        val fakeStubService = RecordingStubService(mapper)
        application {
            attributes.put(DependenciesKey, buildFakeDependency().copy(mapper = mapper, stubService = fakeStubService))
            mockGateway()
        }

        val resp = client.delete("/_proxy-api/stubs/stub-1")

        assertEquals(HttpStatusCode.NoContent, resp.status)
        assertEquals(listOf("stub-1"), fakeStubService.deleteCalls)
    }


    // --- helpers ---

    private fun validCreateStubRequest(): CreateStubRequest =
        CreateStubRequest(
            request = ReqMatch(
                method = ReqMatchMethods.GET,
                url = UrlMatch(type = UrlMatchType.EXACT, value = "/ping"),
                headers = emptyMap(),
                body = null
            ),
            response = RespDef(
                mode = RespMode.STATIC,
                status = 200,
                headers = mapOf("Content-Type" to "application/json"),
                bodyText = "{\"ok\":true}",
                bodyJson = null,
                patch = null
            ),
            ephemeral = Ephemeral(uses = 2, ttlMs = 10000)
        )

    private fun validCreateStubJson(): String =
        mapper.writeValueAsString(validCreateStubRequest())


    private class RecordingStubService(private val mapper: ObjectMapper) : StubService {
        data class CreateCall(val dto: CreateStubRequest, val sessionId: String)
        val createCalls = mutableListOf<CreateCall>()
        var listCalls: Int = 0
        var deleteCalls: MutableList<String> = mutableListOf()

        var listResponseBody: String = "[]"

        override fun create(dto: CreateStubRequest, sessionId: String): Response {
            createCalls += CreateCall(dto, sessionId)
            return json(201, """{"id":"stub-1","summary":"${dto.request.method} ${dto.request.url.value}"}""")
        }

        override fun list(): Response {
            listCalls++
            return json(200, listResponseBody)
        }

        override fun delete(id: String): Response {
            deleteCalls += id
            return Response.response().status(204).build()
        }

        override fun syncFromDb() {
            // no-op for recording fake
        }

        private fun json(code: Int, body: String): Response =
            Response.response()
                .status(code)
                .headers(HttpHeaders(HttpHeader.httpHeader(Headers.CONTENT_TYPE, Headers.JSON)))
                .body(body)
                .build()
    }
}
