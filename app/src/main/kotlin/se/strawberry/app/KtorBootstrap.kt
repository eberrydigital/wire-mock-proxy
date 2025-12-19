package se.strawberry.app

import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory
import org.koin.ktor.plugin.Koin
import se.strawberry.api.mockGateway
import se.strawberry.config.AppConfig
import se.strawberry.di.*

object KtorBootstrap {
    private val log = LoggerFactory.getLogger(KtorBootstrap::class.java)

    fun start(
        cfg: AppConfig,
        installKoin: Boolean = true
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        val server = embeddedServer(Netty, host = cfg.hostAddress, port = cfg.ktorApiPort) {
            if (installKoin) {
                install(Koin) {
                    modules(
                        configModule,
                        infraModule,
                        repositoryModule,
                        serviceModule,
                        wiremockExtensionsModule,
                        wireMockServerModule,
                        wiremockRuntimeModule,
                    )
                }
            }
            mockGateway()
        }

        server.start(wait = false)
        log.info("Ktor API started on {}:{}", cfg.hostAddress, cfg.ktorApiPort)
        return server
    }
}
