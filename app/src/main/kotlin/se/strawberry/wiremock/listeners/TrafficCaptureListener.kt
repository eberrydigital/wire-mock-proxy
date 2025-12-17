package se.strawberry.wiremock.listeners

import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ServeEventListener
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import se.strawberry.common.Headers
import se.strawberry.common.Priorities.PROXY_FALLBACK
import se.strawberry.repository.traffic.RecordedRequestRepository.RecordedRequest
import se.strawberry.service.traffic.TrafficPersister

class TrafficCaptureListener(
    private val persister: TrafficPersister
) : ServeEventListener {

    override fun getName(): String = "traffic-capture-listener"

    override fun afterComplete(serveEvent: ServeEvent, parameters: Parameters) {
        val req = serveEvent.request
        val res = serveEvent.response

        val sessionId = req.getHeader(Headers.X_MOCK_SESSION_ID)
        if (sessionId.isNullOrBlank()) return

        val recorded = RecordedRequest(
            id = serveEvent.id.toString(),
            sessionId = sessionId,
            method = req.method.value(),
            path = req.url,
            query = io.ktor.http.parseQueryString(req.url.substringAfter("?", "")).entries()
                .associate { it.key to it.value.first() },
            headers = req.headers.all().associate { it.key() to it.firstValue() },
            body = req.bodyAsString,
            responseStatus = res.status,
            responseHeaders = res.headers?.all()?.associate { it.key() to it.firstValue() } ?: emptyMap(),
            responseBody = res.bodyAsString,
            timestamp = req.loggedDate.time,
            duration = serveEvent.timing.totalTime.toLong(),
            stubbed = serveEvent.stubMapping != null && serveEvent.stubMapping.priority != PROXY_FALLBACK
        )

        persister.capture(recorded)
    }
}
