package tests.unit

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.LoggedResponse
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.service.request.RequestServiceImpl
import se.strawberry.service.wiremock.WireMockClient
import java.util.Date
import java.util.UUID

class RequestServiceImplTest {

    private val mapper: ObjectMapper = Json.mapper

    @Test
    fun `list should filter by sessionId and return list of models`() {
        val client = mockk<WireMockClient>()
        val service = RequestServiceImpl(mapper, client)

        val ev1 = serveEvent(
            id = "1",
            url = "/api/test-1",
            method = "GET",
            status = 200,
            sessionId = "s-1",
            loggedDate = Date(2000)
        )

        val ev2 = serveEvent(
            id = "2",
            url = "/api/test-2",
            method = "POST",
            status = 500,
            sessionId = "s-2",
            loggedDate = Date(3000)
        )

        every { client.listServeEvents() } returns listOf(ev1, ev2)

        val result = service.list(mapOf("sessionId" to "s-2", "limit" to "200"))


        assertEquals(1, result.size)
        assertEquals("00000000-0000-0000-0000-000000000002", result[0].id)
        assertEquals("/api/test-2", result[0].request.url)
        assertEquals("POST", result[0].request.method)
        assertEquals(500, result[0].response.status)
    }

    @Test
    fun `byId should return null when not found`() {
        val client = mockk<WireMockClient>()
        val service = RequestServiceImpl(mapper, client)

        every { client.findServeEvent("missing") } returns null

        val result = service.byId("missing")

        assertNull(result)
    }

    @Test
    fun `clear should call resetRequests`() {
        val client = mockk<WireMockClient>()
        val service = RequestServiceImpl(mapper, client)

        every { client.resetRequests() } returns Unit

        service.clear()

        verify { client.resetRequests() }
    }

    // ---- helpers ----

    private fun serveEvent(
        id: String,
        url: String,
        method: String,
        status: Int,
        sessionId: String?,
        loggedDate: Date
    ): ServeEvent {

        val loggedRequest = mockk<LoggedRequest>()
        every { loggedRequest.url } returns url
        every { loggedRequest.method.value() } returns method
        every { loggedRequest.loggedDate } returns loggedDate
        every { loggedRequest.bodyAsString } returns ""

        val requestHeaders = mockk<HttpHeaders>(relaxed = true)
        every { requestHeaders.keys() } returns emptySet()
        every { loggedRequest.headers } returns requestHeaders

        every { loggedRequest.getHeader(Headers.X_MOCK_SESSION_ID) } returns sessionId
        every { loggedRequest.getHeader("Content-Type") } returns "application/json"

        val responseHeaders = mockk<HttpHeaders>(relaxed = true)
        every { responseHeaders.keys() } returns emptySet()

        val loggedResponse = mockk<LoggedResponse>()
        every { loggedResponse.status } returns status
        every { loggedResponse.headers } returns responseHeaders
        every { loggedResponse.bodyAsString } returns ""

        val serveEvent = mockk<ServeEvent>()
        every { serveEvent.id } returns UUID.fromString(
            "00000000-0000-0000-0000-${id.padStart(12, '0')}"
        )
        every { serveEvent.request } returns loggedRequest
        every { serveEvent.response } returns loggedResponse
        every { serveEvent.timing } returns null

        return serveEvent
    }
}
