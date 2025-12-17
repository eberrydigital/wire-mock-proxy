package se.strawberry.app

import se.strawberry.config.AppConfigLoader
import se.strawberry.infrastructure.dynamo.DynamoBootstrap
import se.strawberry.infrastructure.dynamo.DynamoClientFactory
import se.strawberry.repository.RepositoryConstants.DYNAMO.SESSION_TABLE_NAME
import se.strawberry.repository.RepositoryConstants.DYNAMO.TRAFFIC_TABLE_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import se.strawberry.wiremock.listeners.TrafficCaptureListener

fun main() {
    val cfg = AppConfigLoader.load()
    val dynamoClient = DynamoClientFactory.create(cfg.dynamo)

    DynamoBootstrap.ensureSessionsTable(
        dynamo = dynamoClient,
        tableName = SESSION_TABLE_NAME
    )
    DynamoBootstrap.ensureTrafficTable(
        dynamo = dynamoClient,
        tableName = TRAFFIC_TABLE_NAME
    )

    val deps = buildDependencies(cfg)
    
    val appScope = CoroutineScope(Dispatchers.Default)
    deps.trafficPersister.start(appScope)

    val trafficListener = TrafficCaptureListener(deps.trafficPersister)
    val wireMock = ServerBootstrap.start(trafficListener)
    val ktor = KtorBootstrap.start(cfg, deps)

    Runtime.getRuntime().addShutdownHook(Thread {
        ktor.stop(1000, 2000)
        wireMock.stop()
    })
}