package se.strawberry

import EnvironmentConfig
import EnvironmentConfig.requireTestKey
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Main")

fun main(args: Array<String>) {
    val argMap = args.mapNotNull {
        val parts = it.removePrefix("--").split("=", limit = 2)
        if (parts.size == 2) parts[0] to parts[1] else null
    }.toMap()

    val proxyTarget = argMap["target"] ?: EnvironmentConfig.proxyTarget
    val port = (argMap["port"] ?: EnvironmentConfig.port.toString()).toInt()
    val adminPort = (argMap["adminPort"] ?: EnvironmentConfig.adminPort.toString()).toInt()
    val bindAddress = argMap["bind"] ?: EnvironmentConfig.bindAddress
    val adminBindAddress = argMap["adminBind"] ?: EnvironmentConfig.adminBindAddress
    val adminApiToken = argMap["adminToken"] ?: EnvironmentConfig.adminApiToken

    // WireMock config

    /**
     * This object doesn't have adminBindAddress nor adminPort so this following part is ommited:
     *   .adminBindAddress(adminBindAddress) // ⚠️ Admin bound to localhost by default for safety
     *         .adminPort(adminPort)
     *         // If you want to require a bearer-like API token on admin calls,
     *         // enable the tokenAuthenticator (WireMock 3.x)
     *         .apply {
     *             if (!adminApiToken.isNullOrBlank()) {
     *                 tokenAuthenticator { token -> token == adminApiToken }
     *             }
     *         }
     *         .maxRequestJournalEntries(5000) // useful for waitForNextHit + auditing
     *         .extensions(
     *             // Register our custom response transformer (used in Milestone 4).
     *             UpstreamPatchTransformer(ObjectMapper().registerModule(KotlinModule.Builder().build()))
     *         )
     *         :TODO find other way.
     */

    val config = options().bindAddress(bindAddress).port(port).maxRequestJournalEntries(5000)


    val server = WireMockServer(config)
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

    if (requireTestKey) {
        server.stubFor(
            any(urlMatching(".*")).withHeader("X-Test-Run-Id", absent()) // <— THIS is the correct "absent" matcher
                .atPriority(1)                          // higher priority than the catch-all proxy
                .willReturn(
                    aResponse().withStatus(400).withHeader("Content-Type", "application/json").withBody("""{"error":"Missing X-Test-Run-Id"}""")
                )
        )
    }

    server.stubFor(
        any(urlMatching(".*")).atPriority(100).willReturn(
                aResponse().proxiedFrom(proxyTarget) // transparent proxy
            )
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop()
    })

}
