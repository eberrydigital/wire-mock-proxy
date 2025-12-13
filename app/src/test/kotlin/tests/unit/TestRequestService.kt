package tests.unit

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.LoggedResponse
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
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
    fun `list should filter by sessionId and return json array`() {
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

        val resp = service.list(mapOf("sessionId" to "s-2", "limit" to "200"))

        assertThat(resp.status, equalTo(200))
        val body = resp.bodyAsString
        assertThat(body, startsWith("["))
        assertThat(body, containsString("\"url\":\"/api/test-2\""))
        assertThat(body, not(containsString("\"url\":\"/api/test-1\"")))

    }

    @Test
    fun `byId should return 404 when not found`() {
        val client = mockk<WireMockClient>()
        val service = RequestServiceImpl(mapper, client)

        every { client.findServeEvent("missing") } returns null

        val resp = service.byId("missing")

        assertThat(resp.status, equalTo(404))
        assertThat(resp.bodyAsString, containsString("not_found"))
    }

    @Test
    fun `clear should call resetRequests and return 204`() {
        val client = mockk<WireMockClient>()
        val service = RequestServiceImpl(mapper, client)

        every { client.resetRequests() } returns Unit

        val resp = service.clear()

        assertThat(resp.status, equalTo(204))
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
        every { loggedRequest.headers } returns HttpHeaders()

        every { loggedRequest.getHeader(Headers.X_MOCK_SESSION_ID) } returns sessionId
        every { loggedRequest.getHeader("Content-Type") } returns "application/json"

        val loggedResponse = mockk<LoggedResponse>()
        every { loggedResponse.status } returns status
        every { loggedResponse.headers } returns HttpHeaders()
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
