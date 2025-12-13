package se.strawberry.app

import se.strawberry.config.AppConfigLoader

fun main() {
    val cfg = AppConfigLoader.load()

    val deps = buildDependencies()

    val wireMock = ServerBootstrap.start()
    val ktor = KtorBootstrap.start(cfg, deps)

    Runtime.getRuntime().addShutdownHook(Thread {
        ktor.stop(1000, 2000)
        wireMock.stop()
    })
}