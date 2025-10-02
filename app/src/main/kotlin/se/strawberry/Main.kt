package se.strawberry

import EnvironmentConfig
import EnvironmentConfig.requireTestKey
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.slf4j.LoggerFactory
import se.strawberry.transform.RequestsApiTransformer
import se.strawberry.admin.ServerRef
import se.strawberry.transform.UpstreamPatchTransformer
import java.nio.file.Files
import java.nio.file.Paths


private val log = LoggerFactory.getLogger("Main")

fun main(args: Array<String>) {
    val argMap = args.mapNotNull {
        val parts = it.removePrefix("--")
            .split("=", limit = 2)
        if (parts.size == 2) parts[0] to parts[1] else null
    }
        .toMap()

    val proxyTarget = argMap["target"] ?: EnvironmentConfig.proxyTarget
    val port = (argMap["port"] ?: EnvironmentConfig.port.toString()).toInt()
    val adminPort = (argMap["adminPort"] ?: EnvironmentConfig.adminPort.toString()).toInt()
    val bindAddress = argMap["bind"] ?: EnvironmentConfig.bindAddress
    val adminBindAddress = argMap["adminBind"] ?: EnvironmentConfig.adminBindAddress
    val adminApiToken = argMap["adminToken"] ?: EnvironmentConfig.adminApiToken
    val wireMockFiles = Paths.get("app/src/main/resources/wiremock").toAbsolutePath()
    Files.createDirectories(wireMockFiles.resolve("mappings"))
    Files.createDirectories(wireMockFiles.resolve("__files"))

    val mapper = ObjectMapper().registerModule(KotlinModule.Builder()
        .build()
    )

    val config = options().bindAddress(bindAddress)
        .port(port)
        .maxRequestJournalEntries(5000)
        .usingFilesUnderDirectory(wireMockFiles.toString())
        .extensions(UpstreamPatchTransformer(mapper), RequestsApiTransformer())


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

    server.stubFor(
        get(urlEqualTo("/_proxy-ui")).atPriority(1)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/html; charset=utf-8")
                    .withBodyFile("ui/index.html")
            )
    )


    server.stubFor(
        any(urlPathMatching("/_proxy-api/.*")).atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withTransformers("requests-api") // имя из getName()
            )
    )

    if (requireTestKey) {
        server.stubFor(
            any(urlMatching(".*")).withHeader("X-Test-Run-Id", absent()) // <— THIS is the correct "absent" matcher
                .atPriority(1)                          // higher priority than the catch-all proxy
                .willReturn(
                    aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":"Missing X-Test-Run-Id"}""")
                )
        )
    }

    server.stubFor(
        any(urlMatching(".*")).atPriority(100)
            .willReturn(
                aResponse().proxiedFrom(proxyTarget) // transparent proxy
            )
    )

    Runtime.getRuntime()
        .addShutdownHook(Thread {
            server.stop()
        })

}
