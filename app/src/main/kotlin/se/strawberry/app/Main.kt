package se.strawberry.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import se.strawberry.config.AppConfigLoader
import se.strawberry.infrastructure.dynamo.DynamoBootstrap
import se.strawberry.infrastructure.dynamo.DynamoClientFactory
import se.strawberry.repository.RepositoryConstants.DYNAMO.SESSION_TABLE_NAME
import se.strawberry.repository.RepositoryConstants.DYNAMO.STUB_TABLE_NAME
import se.strawberry.repository.RepositoryConstants.DYNAMO.TRAFFIC_TABLE_NAME

fun main() {
    val config = AppConfigLoader.load()
    val deps = buildDependencies(config)

    val dynamoClient = DynamoClientFactory.create(config.dynamo)

    DynamoBootstrap.ensureSessionsTable(
        dynamo = dynamoClient,
        tableName = SESSION_TABLE_NAME
    )
    // Ensure DB tables
    DynamoBootstrap.ensureTrafficTable(dynamoClient, TRAFFIC_TABLE_NAME)
    DynamoBootstrap.ensureStubsTable(dynamoClient, STUB_TABLE_NAME)

    val appScope = CoroutineScope(Dispatchers.Default)
    deps.trafficPersister.start(appScope)

    val server = ServerBootstrap.start(config, deps)
    
    // Sync stubs from DB to WireMock
    deps.stubService.syncFromDb()

    val ktor = KtorBootstrap.start(config, deps)

    Runtime.getRuntime().addShutdownHook(Thread {
        ktor.stop(1000, 2000)
        server.stop()
    })
}