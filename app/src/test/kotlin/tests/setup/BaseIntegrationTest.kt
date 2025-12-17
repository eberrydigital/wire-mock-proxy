package tests.setup

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import se.strawberry.app.KtorBootstrap
import se.strawberry.app.ServerBootstrap
import se.strawberry.app.buildDependencies
import se.strawberry.config.AppConfigLoader
import se.strawberry.config.Env
import se.strawberry.config.EnvVar
import se.strawberry.repository.RepositoryConstants.DYNAMO.SESSION_TABLE_NAME
import se.strawberry.repository.RepositoryConstants.DYNAMO.TRAFFIC_TABLE_NAME
import se.strawberry.wiremock.listeners.TrafficCaptureListener
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.util.concurrent.TimeUnit

/**
 * Base class for integration tests with real DynamoDB container.
 * Uses Testcontainers to automatically manage DynamoDB lifecycle.
 */
@Testcontainers
@ExtendWith(SystemStubsExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseIntegrationTest {

    companion object {
        /**
         * LocalStack container with DynamoDB service
         * Shared across all tests in the class for performance
         */
        @Container
        @JvmStatic
        val localstack: LocalStackContainer = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0")
        ).withServices(Service.DYNAMODB)
    }

    protected lateinit var upstream: WireMockServer
    protected lateinit var proxy: WireMockServer
    protected lateinit var ktorApp: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    protected lateinit var http: OkHttpClient
    protected lateinit var upstreamServiceName: String
    protected lateinit var dynamoClient: DynamoDbClient
    protected lateinit var deps: se.strawberry.app.AppDependencies

    @SystemStub
    protected val env = EnvironmentVariables()

    @BeforeAll
    fun setUpSuite() {
        http = OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        // Initialize DynamoDB client pointing to LocalStack
        dynamoClient = DynamoDbClient.builder()
            .endpointOverride(localstack.getEndpointOverride(Service.DYNAMODB))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.accessKey,
                        localstack.secretKey
                    )
                )
            )
            .region(Region.of(localstack.region))
            .build()

        // Create DynamoDB tables
        createTables()
    }

    @BeforeEach
    fun setUp() {
        setupEnvironment()

        // Start upstream service (WireMock)
        upstream = WireMockServer(
            options()
                .port(443) // one from DYN_ALLOWED_PORTS
                .notifier(Slf4jNotifier(false))
                .disableRequestJournal()
        )
        upstream.start()

        upstreamServiceName = "testInstanceOfWireMockServer"
        env.set(EnvVar.ServiceMap.key, "$upstreamServiceName=${upstreamBaseUrl()}")

        // Start proxy (WireMock ingress)
        // Start proxy (WireMock ingress)
        val cfg = AppConfigLoader.load()
        deps = buildDependencies(cfg)
        val appScope = CoroutineScope(Dispatchers.Default)
        deps.trafficPersister.start(appScope)

        proxy = ServerBootstrap.start(cfg, deps)

        // Start Ktor API
        ktorApp = KtorBootstrap.start(cfg, deps)
    }

    @AfterEach
    fun tearDown() {
        try {
            proxy.stop()
        } catch (_: Throwable) {
        }
        try {
            upstream.stop()
        } catch (_: Throwable) {
        }
        try {
            ktorApp.stop(1000, 2000)
        } catch (_: Throwable) {
        }

        // Clean up DynamoDB tables for next test
        cleanupTables()
    }

    @AfterAll
    fun tearDownSuite() {
        try {
            dynamoClient.close()
        } catch (_: Throwable) {
        }
    }

    protected fun upstreamBaseUrl(): String = "http://localhost:${upstream.port()}"
    protected fun apiBaseUrl(): String = "http://localhost:${Env.int(EnvVar.KtorApiPort)}"

    protected fun createSession(id: String) {
        val session = se.strawberry.repository.session.SessionRepository.Session(
            id = id,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000, 
            status = se.strawberry.repository.session.SessionRepository.Session.Status.ACTIVE
        )
        // Use repository to create in DB (LocalStack)
        deps.sessionRepository.create(session)
    }

    /**
     * Setup environment variables for the test
     */
    private fun setupEnvironment() {
        loadEnv()
        // DynamoDB configuration pointing to LocalStack
        env.set(EnvVar.DynamoEndpoint.key, localstack.getEndpointOverride(Service.DYNAMODB).toString())
        env.set(EnvVar.AwsRegion.key, localstack.region)
        env.set(EnvVar.DynamoSessionsTable.key, SESSION_TABLE_NAME)
        env.set("AWS_ACCESS_KEY_ID", localstack.accessKey)
        env.set("AWS_SECRET_ACCESS_KEY", localstack.secretKey)
    }

    /**
     * Load environment variables from .env.test file
     */
    private fun loadEnv() {
        val dotenv = dotenv {
            filename = ".env.test"
            ignoreIfMalformed = true
            ignoreIfMissing = false
        }

        dotenv.entries().forEach { entry ->
            env.set(entry.key, entry.value)
        }
    }

    /**
     * Create required DynamoDB tables
     */
    private fun createTables() {
        try {
            // Create sessions table
            dynamoClient.createTable(
                CreateTableRequest.builder()
                    .tableName(SESSION_TABLE_NAME)
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("sessionId")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("sessionId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build()
            )
        } catch (_: ResourceInUseException) {
            // Table already exists, ignore
        }

        try {
            // Create proxy-traffic table
            dynamoClient.createTable(
                CreateTableRequest.builder()
                    .tableName(TRAFFIC_TABLE_NAME)
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("sessionId")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("sessionId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build()
            )
        } catch (_: ResourceInUseException) {
            // Table already exists, ignore
        }
    }

    /**
     * Clean up data from tables between tests
     */
    private fun cleanupTables() {
        try {
            // Scan and delete all items from sessions table
            val sessionsItems = dynamoClient.scan(
                ScanRequest.builder()
                    .tableName(SESSION_TABLE_NAME)
                    .build()
            ).items()

            sessionsItems.forEach { item ->
                dynamoClient.deleteItem(
                    DeleteItemRequest.builder()
                        .tableName(SESSION_TABLE_NAME)
                        .key(mapOf("sessionId" to item["sessionId"]))
                        .build()
                )
            }

            // Scan and delete all items from proxy-traffic table
            val trafficItems = dynamoClient.scan(
                ScanRequest.builder()
                    .tableName(TRAFFIC_TABLE_NAME)
                    .build()
            ).items()

            trafficItems.forEach { item ->
                dynamoClient.deleteItem(
                    DeleteItemRequest.builder()
                        .tableName(TRAFFIC_TABLE_NAME)
                        .key(mapOf("sessionId" to item["sessionId"]))
                        .build()
                )
            }
        } catch (_: Throwable) {
            // Ignore cleanup errors
        }
    }
}

