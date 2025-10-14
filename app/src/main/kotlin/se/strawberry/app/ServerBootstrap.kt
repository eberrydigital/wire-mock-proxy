package se.strawberry.app


import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.slf4j.LoggerFactory
import se.strawberry.admin.ServerRef
import se.strawberry.api.RequestsApiTransformer
import se.strawberry.common.Headers.X_MOCK_TARGET_SERVICE
import se.strawberry.common.Json
import se.strawberry.common.Paths.API_PREFIX
import se.strawberry.common.Paths.UI_ASSETS_PREFIX
import se.strawberry.common.Paths.UI_ROOT
import se.strawberry.common.Priorities.PROXY_FALLBACK
import se.strawberry.common.Priorities.UI
import se.strawberry.common.TransformerNames
import se.strawberry.config.EnvironmentConfig
import se.strawberry.config.ServiceRegistry
import se.strawberry.wiremock.filters.DynamicRoutingGuard
import se.strawberry.wiremock.listeners.EphemeralServeEventListener
import se.strawberry.wiremock.matchers.TtlGuardMatcher
import se.strawberry.wiremock.templating.ServiceTemplateHelpers
import se.strawberry.wiremock.transformers.UpstreamPatchTransformer


object ServerBootstrap {
    private val log = LoggerFactory.getLogger(ServerBootstrap::class.java)
    val mapper = Json.mapper

    fun start(): WireMockServer {

        val cfg = EnvironmentConfig
        val services = ServiceRegistry.fromEnv()

        val server = WireMockServer(
            options()
                .port(cfg.port)
                .bindAddress(cfg.bindAddress)
                .usingFilesUnderClasspath("wiremock") // Ui Files reside there
                // High level order of processing:
                // 1) DynamicRoutingGuard — let through only correct external requests (headers, service names, ports).
                // 2) TtlGuardMatcher — TTL (time to live) stub logic.
                // 3) RequestsApiTransformer — API that our frontend communicates with, starts with /_proxy-api.
                // 4) UpstreamPatchTransformer — patch responses from upstream services if needed.
                // 5) EphemeralServeEventListener — decrement uses/TTL, remove stubs if needed.
                // 6) ServiceTemplateHelpers — helper functions for response templating.
                .extensions(
                    DynamicRoutingGuard(services),
                    TtlGuardMatcher(),
                    UpstreamPatchTransformer(mapper),
                    RequestsApiTransformer(),
                    EphemeralServeEventListener(),
                    ServiceTemplateHelpers(services)
                )
                .templatingEnabled(true)
        )

        server.start()
        ServerRef.server = server
        log.info("WireMock proxy started on {}:{}; services: {}", cfg.bindAddress, cfg.port, services)

        // Ui Files
        registerRulesForFrontendRequests(server)

        server.stubFor(
            any(urlPathMatching("$API_PREFIX/.*")).atPriority(UI)
                .willReturn(
                    aResponse()
                        .withTransformers(TransformerNames.REQUESTS_API)
                )
        )

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

    private fun registerRulesForFrontendRequests(server: WireMockServer) {
        server.stubFor(
            get(urlEqualTo(UI_ROOT)).atPriority(UI)
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withHeader("Cache-Control", "no-store")
                        .withBodyFile("ui/index.html")
                )
        )

        server.stubFor(
            get(urlPathMatching("$UI_ASSETS_PREFIX/.*")).atPriority(UI)
                .willReturn(
                    aResponse()
                        .withHeader("Cache-Control", "no-store")
                        .withTransformerParameter("pathPrefix", "ui/assets")
                        .withBodyFile("ui/assets/styles.css")
                )
        )

        server.stubFor(
            get(urlEqualTo("$UI_ASSETS_PREFIX/app.js")).atPriority(UI)
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/javascript; charset=utf-8")
                        .withHeader("Cache-Control", "public, max-age=31536000, immutable")
                        .withBodyFile("ui/assets/app.js")
                )
        )

    }
}