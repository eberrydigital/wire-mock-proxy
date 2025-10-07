package se.strawberry.app

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.slf4j.LoggerFactory
import se.strawberry.admin.ServerRef
import se.strawberry.api.RequestsApiTransformer
import se.strawberry.common.Json
import se.strawberry.common.Paths.API_PREFIX
import se.strawberry.common.Paths.UI_ASSETS_PREFIX
import se.strawberry.common.Paths.UI_ROOT
import se.strawberry.common.Priorities.PROXY_FALLBACK
import se.strawberry.common.Priorities.UI
import se.strawberry.common.TransformerNames
import se.strawberry.config.EnvironmentConfig
import se.strawberry.config.ServiceRegistry
import se.strawberry.extensions.filters.DynamicRoutingGuard
import se.strawberry.extensions.listeners.EphemeralServeEventListener
import se.strawberry.extensions.matchers.TtlGuardMatcher
import se.strawberry.extensions.templating.ServiceTemplateHelpers
import se.strawberry.extensions.transformers.UpstreamPatchTransformer
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path


private fun resolveWireMockFilesDir(): Path {
    val override = System.getProperty("WIREMOCK_FILES") ?: System.getenv("WIREMOCK_FILES")
    if (!override.isNullOrBlank()) {
        val p = Paths.get(override).toAbsolutePath().normalize()
        Files.createDirectories(p.resolve("mappings"))
        Files.createDirectories(p.resolve("__files"))
        log.info("WireMock files dir (override): {}", p)
        return p
    }

    val candidates = listOf(
        Paths.get("app/src/main/resources/wiremock"),
        Paths.get("src/main/resources/wiremock"),
        Paths.get("resources/wiremock")
    )

    for (cand in candidates) {
        val p = cand.toAbsolutePath().normalize()
        if (Files.isDirectory(p)) {
            log.info("WireMock files dir (detected): {}", p)
            return p
        }
    }

    val fallback = Paths.get("build/wiremock").toAbsolutePath().normalize()
    Files.createDirectories(fallback.resolve("mappings"))
    Files.createDirectories(fallback.resolve("__files"))
    log.warn("WireMock files dir (fallback): {}", fallback)
    return fallback
}


private val log = LoggerFactory.getLogger("Main")

fun main(){
    val proxyTarget = EnvironmentConfig.proxyTarget
    val port = EnvironmentConfig.port
    val bindAddress = EnvironmentConfig.bindAddress
    val wireMockFiles = resolveWireMockFilesDir()
    Files.createDirectories(wireMockFiles.resolve("mappings"))
    Files.createDirectories(wireMockFiles.resolve("__files"))

    val mapper = Json.mapper

    val serviceRegistry = ServiceRegistry.fromEnv()
    log.info("SERVICE_MAP loaded: {}", serviceRegistry)

    val config = options().bindAddress(bindAddress)
        .port(port)
        .maxRequestJournalEntries(5000)
        .usingFilesUnderDirectory(wireMockFiles.toString())
        .extensions(
            ServiceTemplateHelpers(serviceRegistry),
            UpstreamPatchTransformer(mapper),
            RequestsApiTransformer(),
            EphemeralServeEventListener(),
            TtlGuardMatcher(),
            DynamicRoutingGuard(serviceRegistry)
        )


    val server = WireMockServer(config)
    ServerRef.server = server
    server.start()

    log.info(
        "WireMock started  target={}  port={} bind={}",
        proxyTarget,
        port,
        bindAddress,
    )

// HTML
    server.stubFor(
        get(urlEqualTo(UI_ROOT)).atPriority(UI)
            .willReturn(aResponse()
                .withHeader("Content-Type", "text/html; charset=utf-8")
                .withHeader("Cache-Control", "no-store")
                .withBodyFile("ui/index.html")
            )
    )


// So that CSS is not proxied
    server.stubFor(
        get(urlEqualTo("$UI_ASSETS_PREFIX/styles.css")).atPriority(UI)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/css; charset=utf-8")
                    .withHeader("Cache-Control", "public, max-age=31536000, immutable")
                    .withBodyFile("ui/assets/styles.css")
            )
    )

// So that JS is not proxied
    server.stubFor(
        get(urlEqualTo("$UI_ASSETS_PREFIX/app.js")).atPriority(UI)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/javascript; charset=utf-8")
                    .withHeader("Cache-Control", "public, max-age=31536000, immutable")
                    .withBodyFile("ui/assets/app.js")
            )
    )


// Our Main API transformer
    server.stubFor(
        any(urlPathMatching("${API_PREFIX}/.*")).atPriority(UI)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withTransformers(TransformerNames.REQUESTS_API)
            )
    )


// Proxy fallback - everything else is simply proxied
    server.stubFor(
        any(urlMatching(".*")).atPriority(PROXY_FALLBACK)
            .willReturn(
                aResponse()
                    .proxiedFrom("{{service-origin name=request.headers.[X-Target-Service]}}")
                    .withTransformers("response-template")
            )
    )

    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
}