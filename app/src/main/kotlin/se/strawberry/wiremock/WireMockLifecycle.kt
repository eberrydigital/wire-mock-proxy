package se.strawberry.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import org.slf4j.LoggerFactory

class WireMockLifecycle(
    private val server: WireMockServer
) {
    private val log = LoggerFactory.getLogger(WireMockLifecycle::class.java)

    fun start() {
        if (!server.isRunning) {
            server.start()
            log.info("WireMock started at http://{}:{}", server.options.bindAddress(), server.port())
        }
    }

    fun stop() {
        if (server.isRunning) {
            server.stop()
            log.info("WireMock stopped")
        }
    }
}
