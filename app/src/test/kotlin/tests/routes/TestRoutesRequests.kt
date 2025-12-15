package tests.routes

import com.fasterxml.jackson.databind.ObjectMapper
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
import se.strawberry.api.models.traffic.HttRequestModel
import se.strawberry.api.models.traffic.HttpResponseModel
import se.strawberry.api.models.traffic.RecordedTrafficInstanceModel
import se.strawberry.common.Json
import se.strawberry.service.request.RequestService

class RequestsRoutesTest {

    private val mapper: ObjectMapper = Json.mapper

    @Test
    fun `GET _proxy-api_requests - forwards query map to service and returns payload`() = testApplication {
        val fake = RecordingRequestService().apply {
            listResult = listOf(
                RecordedTrafficInstanceModel(
                    id = "1",
                    receivedAt = System.currentTimeMillis(),
                    request = HttRequestModel("GET", "/test", emptyMap()),
                    response = HttpResponseModel(200, emptyMap())
                )
            )
        }
        application {
            attributes.put(DependenciesKey, buildFakeDependency().copy(mapper = mapper, requestService = fake))
            mockGateway()
        }

        val resp = client.get("/_proxy-api/traffic?method=GET&limit=10")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"id\":\"1\""))

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

        val resp = client.get("/_proxy-api/traffic/%20")

        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertTrue(resp.bodyAsText().contains("missing_id"))
        assertEquals(0, fake.byIdCalls.size)
    }

    // --- helpers ---

    private class RecordingRequestService : RequestService {
        var listResult: List<RecordedTrafficInstanceModel> = emptyList()
        val listCalls = mutableListOf<Map<String, String>>()
        val byIdCalls = mutableListOf<String>()
        var clearCalls: Int = 0
        var exportCalls: Int = 0

        override fun list(query: Map<String, String>): List<RecordedTrafficInstanceModel> {
            listCalls += query
            return listResult
        }

        override fun byId(id: String): RecordedTrafficInstanceModel? {
            byIdCalls += id
            return RecordedTrafficInstanceModel(
                id = id,
                receivedAt = System.currentTimeMillis(),
                request = HttRequestModel("GET", "/test", emptyMap()),
                response = HttpResponseModel(200, emptyMap())
            )
        }

        override fun clear() {
            clearCalls++
        }

        override fun exportAsNdjson(): String {
            exportCalls++
            return """{"id":"1"}\n"""
        }
    }
}
