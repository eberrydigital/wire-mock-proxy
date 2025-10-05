package se.strawberry.app

import se.strawberry.config.EnvironmentConfig
import se.strawberry.config.EnvironmentConfig.requireTestKey
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.slf4j.LoggerFactory
import se.strawberry.api.RequestsApiTransformer
import se.strawberry.admin.ServerRef
import se.strawberry.common.Json
import se.strawberry.common.Paths.API_PREFIX
import se.strawberry.common.Paths.UI_ASSETS_PREFIX
import se.strawberry.common.Paths.UI_ROOT
import se.strawberry.common.Priorities.PROXY_FALLBACK
import se.strawberry.common.Priorities.UI
import se.strawberry.extensions.listeners.EphemeralServeEventListener
import se.strawberry.extensions.matchers.TtlGuardMatcher
import se.strawberry.extensions.transformers.UpstreamPatchTransformer
import java.nio.file.Files
import java.nio.file.Paths

private val log = LoggerFactory.getLogger("Main")

fun main(args: Array<String>) {
    val argMap = args.mapNotNull {
        val parts = it.removePrefix("--")
            .split("=", limit = 2)
        if (parts.size == 2) parts[0] to parts[1] else null
    }.toMap()

    val proxyTarget = argMap["target"] ?: EnvironmentConfig.proxyTarget
    val port = (argMap["port"] ?: EnvironmentConfig.port.toString()).toInt()
    val adminPort = (argMap["adminPort"] ?: EnvironmentConfig.adminPort.toString()).toInt()
    val bindAddress = argMap["bind"] ?: EnvironmentConfig.bindAddress
    val adminBindAddress = argMap["adminBind"] ?: EnvironmentConfig.adminBindAddress
    val adminApiToken = argMap["adminToken"] ?: EnvironmentConfig.adminApiToken
    val wireMockFiles = Paths.get("app/src/main/resources/wiremock").toAbsolutePath()
    Files.createDirectories(wireMockFiles.resolve("mappings"))
    Files.createDirectories(wireMockFiles.resolve("__files"))

    val mapper = Json.mapper

    val config = options().bindAddress(bindAddress)
        .port(port)
        .maxRequestJournalEntries(5000)
        .usingFilesUnderDirectory(wireMockFiles.toString())
        .extensions(UpstreamPatchTransformer(mapper), RequestsApiTransformer(),  EphemeralServeEventListener(),
            TtlGuardMatcher()
        )



    val server = WireMockServer(config)
    ServerRef.server = server
    server.start()

    log.info(
        "WireMock started  target={}  port={}  adminPort={}  bind={}  adminBind={}  requireTestKey={}  tokenEnabled={}",
        proxyTarget,
        port,
        adminPort,
        bindAddress,
        adminBindAddress,
        requireTestKey,
        !adminApiToken.isNullOrBlank()
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


// So that CSS is not proxied :TODO write a better solution for it, maybe through transformer
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

    server.stubFor(
        get(urlEqualTo(UI_ROOT)).atPriority(UI)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/html; charset=utf-8")
                    .withBodyFile("ui/index.html")
            )
    )


    server.stubFor(
        any(urlPathMatching("${API_PREFIX}/.*")).atPriority(UI)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withTransformers("requests-api") // имя из getName()
            )
    )

    if (requireTestKey) {
        server.stubFor(
            any(urlMatching(".*")).withHeader("X-Test-Run-Id", absent()) // <— THIS is the correct "absent" matcher
                .atPriority(UI)                          // higher priority than the catch-all proxy
                .willReturn(
                    aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":"Missing X-Test-Run-Id"}""")
                )
        )
    }

    server.stubFor(
        any(urlMatching(".*")).atPriority(PROXY_FALLBACK)
            .willReturn(
                aResponse().proxiedFrom(proxyTarget) // transparent proxy
            )
    )

    Runtime.getRuntime()
        .addShutdownHook(Thread {
            server.stop()
        })

}
