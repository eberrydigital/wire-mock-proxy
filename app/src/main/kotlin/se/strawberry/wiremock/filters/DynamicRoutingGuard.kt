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
import se.strawberry.config.ServiceRegistry

class DynamicRoutingGuard(
    private val services: ServiceRegistry
) : StubRequestFilterV2 {
    private val log = LoggerFactory.getLogger(javaClass)

    private val allowedPorts: Set<Int> =  setOf(80, 443)
    override fun getName() = FilterNames.DYNAMIC_ROUTING_GUARD

    private fun isInternalPath(url: String): Boolean {
        return url.startsWith(API_PREFIX) ||
                url.startsWith(UI_ROOT) ||
                url.startsWith(UI_ASSETS_PREFIX) ||
                url.startsWith(ADMIN_PREFIX)
    }

    override fun filter(request: Request, serveEvent: ServeEvent): RequestFilterAction {
        val url = request.url
        if (isInternalPath(url)) return RequestFilterAction.continueWith(request)

        val svcHeader = request.header(Headers.X_MOCK_TARGET_SERVICE)
        val svc = if (svcHeader.isPresent) svcHeader.values()[0] else null

        if (svc.isNullOrBlank()) return stop(400, "missing-service")

        val origin = services.resolve(svc) ?: return stop(404, "unknown-service")

        // Optional: basic scheme check (defensive)
        if (!origin.startsWith("http://") && !origin.startsWith("https://")) {
            return stop(400, "invalid-origin")
        }

        // Port policy
        val afterScheme = origin.substringAfter("://")
        val hostPort = afterScheme.substringBefore('/')
        val explicitPort = hostPort.substringAfterLast(':', "").takeIf { ':' in hostPort }?.toIntOrNull()
        if (explicitPort != null && explicitPort !in allowedPorts) return stop(403, "bad-port")

        log.info("Dynamic routing: service='{}' -> origin='{}' url='{}'", svc, origin, url)

        return RequestFilterAction.continueWith(request)
    }

    private fun stop(code: Int, reason: String): RequestFilterAction {
        log.warn("Dynamic routing rejected: {}", reason)
        val rd = ResponseDefinitionBuilder()
            .withStatus(code)
            .withHeader("Content-Type", "application/json; charset=utf-8")
            .withBody("""{"error":"dynamic-routing-denied","reason":"$reason"}""")
            .build()
        return RequestFilterAction.stopWith(rd)
    }
}
