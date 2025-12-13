package se.strawberry.app

import se.strawberry.config.AppConfigLoader
import se.strawberry.infrastructure.dynamo.DynamoBootstrap
import se.strawberry.infrastructure.dynamo.DynamoClientFactory

fun main() {
    val cfg = AppConfigLoader.load()
    val dynamoClient = DynamoClientFactory.create(cfg.dynamo)

    DynamoBootstrap.ensureSessionsTable(
        dynamo = dynamoClient,
        tableName = cfg.dynamo.sessionsTable
    )

    val deps = buildDependencies(cfg)

    val wireMock = ServerBootstrap.start()
    val ktor = KtorBootstrap.start(cfg, deps)

    Runtime.getRuntime().addShutdownHook(Thread {
        ktor.stop(1000, 2000)
        wireMock.stop()
    })
}