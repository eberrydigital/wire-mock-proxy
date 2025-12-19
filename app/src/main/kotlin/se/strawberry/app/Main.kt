package se.strawberry.app

import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.java.KoinJavaComponent.getKoin
import se.strawberry.config.AppConfigLoader
import se.strawberry.di.*
import se.strawberry.infrastructure.dynamo.DynamoBootstrap
import se.strawberry.repository.RepositoryConstants.DYNAMO.SESSION_TABLE_NAME
import se.strawberry.repository.RepositoryConstants.DYNAMO.STUB_TABLE_NAME
import se.strawberry.repository.RepositoryConstants.DYNAMO.TRAFFIC_TABLE_NAME
import se.strawberry.wiremock.WireMockBootstrap
import se.strawberry.wiremock.WireMockLifecycle
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

fun main() {

    startKoin {
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

    val koin = getKoin()
    val dynamoClient = koin.get<DynamoDbClient>()

    DynamoBootstrap.ensureSessionsTable(dynamo = dynamoClient, tableName = SESSION_TABLE_NAME)
    DynamoBootstrap.ensureTrafficTable(dynamoClient, TRAFFIC_TABLE_NAME)
    DynamoBootstrap.ensureStubsTable(dynamoClient, STUB_TABLE_NAME)

    val wireMockLifecycle = koin.get<WireMockLifecycle>()
    val wireMockBootstrap = koin.get<WireMockBootstrap>()

    wireMockLifecycle.start()
    wireMockBootstrap.initFallbackProxy()

    val config = AppConfigLoader.load()
    val ktor = KtorBootstrap.start(cfg= config,  installKoin = false)


    Runtime.getRuntime().addShutdownHook(Thread {
        try {
            ktor.stop(1000, 2000)
        } finally {
            wireMockLifecycle.stop()
            stopKoin()
        }
    })
}