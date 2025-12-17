package se.strawberry.app


import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.slf4j.LoggerFactory
import se.strawberry.admin.ServerRef
import se.strawberry.common.Headers.X_MOCK_TARGET_SERVICE
import se.strawberry.common.Json
import se.strawberry.common.Priorities.PROXY_FALLBACK
import se.strawberry.config.AppConfig
import se.strawberry.wiremock.filters.DynamicRoutingGuard
import se.strawberry.wiremock.listeners.EphemeralServeEventListener
import se.strawberry.wiremock.matchers.TtlGuardMatcher
import se.strawberry.wiremock.templating.ServiceTemplateHelpers


object ServerBootstrap {
    private val log = LoggerFactory.getLogger(ServerBootstrap::class.java)
    val mapper = Json.mapper

    fun start(cfg: AppConfig, deps: AppDependencies): WireMockServer {
        val trafficListener = se.strawberry.wiremock.listeners.TrafficCaptureListener(deps.trafficPersister)
        
        val server = WireMockServer(
            options()
                .port(cfg.wireMockServerPort)
                .bindAddress(cfg.hostAddress)
                .templatingEnabled(true)
                .disableRequestJournal() // Disable in-memory journal to save memory
                // 1) TtlGuardMatcher — TTL (time to live) stub logic.
                // 2) EphemeralServeEventListener — decrement uses/TTL, remove stubs if needed.
                // 3) ServiceTemplateHelpers — helper functions for response templating.
                .extensions(
                    DynamicRoutingGuard(cfg.services, cfg.allowedPorts, deps.sessionRepository),
                    TtlGuardMatcher(),
                    EphemeralServeEventListener(),
                    trafficListener,
                    ServiceTemplateHelpers(cfg.services),
                )
                .templatingEnabled(true)
        )

        server.start()
        ServerRef.server = server
        log.info("WireMock proxy started on {}:{}; services: {}", cfg.hostAddress, cfg.wireMockServerPort, cfg.services.keys)

        server.stubFor(
            any(urlMatching(".*")).atPriority(PROXY_FALLBACK)
                .willReturn(
                    aResponse()
                        .proxiedFrom("{{service-origin name=request.headers.[$X_MOCK_TARGET_SERVICE]}}")
                        .withTransformers("response-template")
                )
        )

        Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
        return server
    }
}