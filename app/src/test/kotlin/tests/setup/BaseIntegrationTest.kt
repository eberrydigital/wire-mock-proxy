package tests.setup

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.netty.NettyApplicationEngine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.*
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import se.strawberry.app.KtorBootstrap
import se.strawberry.app.ServerBootstrap
import se.strawberry.app.buildDependencies
import se.strawberry.common.Headers.X_MOCK_SESSION_ID
import se.strawberry.common.Headers.X_MOCK_TARGET_SERVICE
import se.strawberry.config.AppConfigLoader
import se.strawberry.config.Env
import se.strawberry.config.EnvVar
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import org.junit.jupiter.api.extension.ExtendWith
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
        proxy = ServerBootstrap.start()

        // Start Ktor API
        val cfg = AppConfigLoader.load()
        val deps = buildDependencies(cfg)
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

    /**
     * Make a proxied call to upstream service
     */
    protected fun call(sessionId: String?, endpoint: String): Response {
        val req = Request.Builder()
            .url("${proxyBaseUrl()}$endpoint")
            .addHeader(X_MOCK_TARGET_SERVICE, upstreamServiceName)
            .apply { if (sessionId != null) addHeader(X_MOCK_SESSION_ID, sessionId) }
            .build()
        return http.newCall(req).execute()
    }

    protected fun proxyBaseUrl(): String = "http://localhost:${proxy.port()}"
    protected fun upstreamBaseUrl(): String = "http://localhost:${upstream.port()}"
    protected fun apiBaseUrl(): String = "http://localhost:${Env.int(EnvVar.KtorApiPort)}"

    /**
     * Setup environment variables for the test
     */
    private fun setupEnvironment() {
        loadEnv()
        // DynamoDB configuration pointing to LocalStack
        env.set(EnvVar.DynamoEndpoint.key, localstack.getEndpointOverride(Service.DYNAMODB).toString())
        env.set(EnvVar.AwsRegion.key, localstack.region)
        env.set(EnvVar.DynamoSessionsTable.key, "proxy-sessions")
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
                    .tableName("proxy-sessions")
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
                    .tableName("proxy-traffic")
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
                    .tableName("proxy-sessions")
                    .build()
            ).items()

            sessionsItems.forEach { item ->
                dynamoClient.deleteItem(
                    DeleteItemRequest.builder()
                        .tableName("proxy-sessions")
                        .key(mapOf("sessionId" to item["sessionId"]))
                        .build()
                )
            }

            // Scan and delete all items from proxy-traffic table
            val trafficItems = dynamoClient.scan(
                ScanRequest.builder()
                    .tableName("proxy-traffic")
                    .build()
            ).items()

            trafficItems.forEach { item ->
                dynamoClient.deleteItem(
                    DeleteItemRequest.builder()
                        .tableName("proxy-traffic")
                        .key(mapOf("sessionId" to item["sessionId"]))
                        .build()
                )
            }
        } catch (_: Throwable) {
            // Ignore cleanup errors
        }
    }
}

