package se.strawberry.wiremock.filters

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilterV2
import org.slf4j.LoggerFactory
import se.strawberry.common.FilterNames
import se.strawberry.common.Headers
import se.strawberry.common.Paths.ADMIN_PREFIX
import se.strawberry.common.Paths.API_PREFIX
import se.strawberry.common.Paths.UI_ASSETS_PREFIX
import se.strawberry.common.Paths.UI_ROOT
import java.net.URI

import se.strawberry.repository.session.SessionRepository

class DynamicRoutingGuard(
    private val services: Map<String, URI>,
    private val allowedPorts: Set<Int>,
    private val sessionRepository: SessionRepository
) : StubRequestFilterV2 {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName(): String = FilterNames.DYNAMIC_ROUTING_GUARD

    override fun filter(request: Request, serveEvent: ServeEvent): RequestFilterAction {
        val url = request.url
        if (isInternalPath(url)) {
            return RequestFilterAction.continueWith(request)
        }

        val svcHeader = request.header(Headers.X_MOCK_TARGET_SERVICE)
        val serviceName = if (svcHeader.isPresent) svcHeader.firstValue() else null
        if (serviceName.isNullOrBlank()) {
            return deny(400, "missing-service", "Header ${Headers.X_MOCK_TARGET_SERVICE} is required")
        }

        val sessionHeader = request.header(Headers.X_MOCK_SESSION_ID)
        val sessionId = if (sessionHeader.isPresent) sessionHeader.firstValue() else null
        if (sessionId.isNullOrBlank()) {
            return deny(400, "missing-session", "Header ${Headers.X_MOCK_SESSION_ID} is required")
        }

        val session = sessionRepository.get(sessionId)
        if (session == null) {
            return deny(403, "invalid-session", "Session not found")
        }
        if (session.status != se.strawberry.repository.session.SessionRepository.Session.Status.ACTIVE) {
            return deny(403, "session-closed", "Session is closed")
        }
        if (session.expiresAt != null && session.expiresAt < System.currentTimeMillis()) {
            return deny(403, "session-expired", "Session has expired")
        }

        val origin = services[serviceName]
            ?: return deny(404, "unknown-service", "Service '$serviceName' is not defined in SERVICE_MAP")

        val scheme = origin.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return deny(400, "invalid-origin", "Unsupported scheme '$scheme' for '$serviceName'")
        }

        val port = effectivePort(origin)
        if (port !in allowedPorts) {
            return deny(403, "bad-port", "Port $port is not in DYN_ALLOWED_PORTS")
        }

        log.debug("Dynamic routing OK: service='{}' origin='{}' url='{}'", serviceName, origin, url)
        return RequestFilterAction.continueWith(request)
    }

    // ----- helpers -----

    private fun isInternalPath(url: String): Boolean =
        url.startsWith(API_PREFIX) || url.startsWith(UI_ROOT) || url.startsWith(UI_ASSETS_PREFIX) || url.startsWith(ADMIN_PREFIX)

    private fun effectivePort(uri: URI): Int {
        val explicit = uri.port
        if (explicit != -1) return explicit
        return when (uri.scheme?.lowercase()) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
    }

    private fun deny(status: Int, code: String, message: String): RequestFilterAction {
        val body = """{"error":"dynamic-routing-denied","reason":"$code","message":"$message"}"""
        val rd = ResponseDefinitionBuilder()
            .withStatus(status)
            .withHeader("Content-Type", "application/json; charset=utf-8")
            .withBody(body)
            .build()
        log.debug("Dynamic routing rejected: {} ({})", code, message)
        return RequestFilterAction.stopWith(rd)
    }
}
