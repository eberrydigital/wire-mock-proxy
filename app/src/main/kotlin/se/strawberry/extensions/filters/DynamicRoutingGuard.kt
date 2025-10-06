package se.strawberry.extensions.filters

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilterV2
import org.slf4j.LoggerFactory
import se.strawberry.common.Headers

class DynamicRoutingGuard : StubRequestFilterV2 {
    private val log = LoggerFactory.getLogger(javaClass)

    private val enabled = envBool("DYN_ROUTING_ENABLED", true)
    private val allowedHosts = envCsv("DYN_ALLOWED_HOSTS").map { it.lowercase() }.toSet()
    private val allowedSuffixes = envCsv("DYN_ALLOWED_SUFFIXES").map { it.lowercase() }.toSet()
    private val allowedPorts = (envCsv("DYN_ALLOWED_PORTS").mapNotNull { it.toIntOrNull() }.toSet()).ifEmpty { setOf(80, 443) }

    override fun getName() = "dynamic-routing-guard"

    override fun filter(request: Request, serveEvent: ServeEvent): RequestFilterAction {
        if (!enabled) return RequestFilterAction.continueWith(request)

        val xfhHeader = request.header(Headers.X_FORWARDED_HOST)
        val xfpHeader = request.header(Headers.X_FORWARDED_PROTO)

        val xfh = if (xfhHeader.isPresent) xfhHeader.values()[0] else null
        val xfp = if (xfpHeader.isPresent) xfpHeader.values()[0] else null

        // If both are not there - it's static proxy — allow. To support current logic with fallback.
        if (xfh.isNullOrBlank() && xfp.isNullOrBlank()) { return RequestFilterAction.continueWith(request) }

        // Only one is 400
        if (xfh.isNullOrBlank() || xfp.isNullOrBlank()) {
            return stop(400, "missing-header")
        }

        val proto = xfp.lowercase()
        if (proto != "http" && proto != "https") {
            return stop(400, "invalid-proto")
        }

        val (host, port) = when (val idx = xfh.indexOf(':')) {
            -1 -> xfh.lowercase() to defaultPort(proto)
            else -> {
                val h = xfh.substring(0, idx).lowercase()
                val p = xfh.substring(idx + 1).toIntOrNull() ?: return stop(400, "invalid-port")
                h to p
            }
        }

        if (port !in allowedPorts) return stop(403, "bad-port")

        val allowAll = allowedHosts.isEmpty() && allowedSuffixes.isEmpty()
        val okHost = host in allowedHosts || allowedSuffixes.any { s ->
            val suf = s.removePrefix(".")
            host == suf || host.endsWith(".$suf")
        }
        if (!allowAll && !okHost) return stop(403, "not-allowed-host")

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

    private fun defaultPort(proto: String) = if (proto == "https") 443 else 80

    private fun envBool(name: String, default: Boolean): Boolean {
        val v = System.getenv(name)?.trim()?.lowercase() ?: return default
        return v == "1" || v == "true" || v == "yes" || v == "on"
    }
    private fun envCsv(name: String) =
        System.getenv(name)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
