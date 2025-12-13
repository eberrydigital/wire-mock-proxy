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
import se.strawberry.app.DependenciesKey
import se.strawberry.app.mockGateway
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.service.request.RequestService

class RequestsRoutesTest {

    private val mapper: ObjectMapper = Json.mapper

    @Test
    fun `GET _proxy-api_requests - forwards query map to service and returns payload`() = testApplication {
        val fake = RecordingRequestService().apply {
            listBody = """[{"id":"1"}]"""
        }
        application {
            attributes.put(DependenciesKey, buildFakeDependency().copy(mapper = mapper, requestService = fake))
            mockGateway()
        }

        val resp = client.get("/_proxy-api/requests?method=GET&limit=10")

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals("""[{"id":"1"}]""", resp.bodyAsText())

        assertEquals(1, fake.listCalls.size)
        val query = fake.listCalls.single()
        assertEquals("GET", query["method"])
        assertEquals("10", query["limit"])
    }

    @Test
    fun `GET _proxy-api_requests by id - blank id returns 400`() = testApplication {
        val fake = RecordingRequestService()
        application {
            attributes.put(DependenciesKey, buildFakeDependency().copy(mapper = mapper, requestService = fake))
            mockGateway()
        }

        val resp = client.get("/_proxy-api/requests/%20")

        // IMPORTANT: this assumes you fixed the route to respondBadRequest("missing_id")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertTrue(resp.bodyAsText().contains("missing_id"))
        assertEquals(0, fake.byIdCalls.size)
    }

    // --- helpers ---

    private class RecordingRequestService : RequestService {
        var listBody: String = "[]"
        val listCalls = mutableListOf<Map<String, String>>()
        val byIdCalls = mutableListOf<String>()
        var clearCalls: Int = 0
        var exportCalls: Int = 0

        override fun list(query: Map<String, String>): Response {
            listCalls += query
            return json(200, listBody)
        }

        override fun byId(id: String): Response {
            byIdCalls += id
            return json(200, """{"id":"$id"}""")
        }

        override fun clear(): Response {
            clearCalls++
            return Response.response().status(204).build()
        }

        override fun export(): Response {
            exportCalls++
            return Response.response()
                .status(200)
                .headers(
                    HttpHeaders(
                        HttpHeader.httpHeader("Content-Type", "application/x-ndjson")
                    )
                )
                .body("""{"id":"1"}\n""")
                .build()
        }

        private fun json(code: Int, body: String): Response =
            Response.response()
                .status(code)
                .headers(HttpHeaders(HttpHeader.httpHeader(Headers.CONTENT_TYPE, Headers.JSON)))
                .body(body)
                .build()
    }
}
