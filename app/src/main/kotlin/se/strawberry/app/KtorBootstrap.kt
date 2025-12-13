package se.strawberry.app

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory
import se.strawberry.config.AppConfig

object KtorBootstrap {
    private val log = LoggerFactory.getLogger(KtorBootstrap::class.java)

    fun start(cfg: AppConfig, deps: AppDependencies): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        val server = embeddedServer(
            Netty,
            host = cfg.bindAddress,
            port = cfg.apiPort
        ) {
            attributes.put(DependenciesKey, deps)
            mockGateway()
        }

        server.start(wait = false)
        log.info("Ktor API started on {}:{}", cfg.bindAddress, cfg.apiPort)
        return server
    }
}
