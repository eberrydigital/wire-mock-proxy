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
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import se.strawberry.app.KtorBootstrap
import se.strawberry.app.ServerBootstrap
import se.strawberry.app.buildDependencies
import se.strawberry.config.AppConfigLoader
import se.strawberry.config.Env
import se.strawberry.config.EnvVar
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

@ExtendWith(SystemStubsExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseTest {

    protected lateinit var upstream: WireMockServer
    protected lateinit var proxy: WireMockServer
    protected lateinit var ktorApp: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    protected lateinit var http: OkHttpClient
    protected lateinit var upstreamServiceName: String
    protected lateinit var deps: se.strawberry.app.AppDependencies

    @SystemStub
    protected val env = EnvironmentVariables()

    @BeforeAll
    fun setUpSuite() {
        http = OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @BeforeEach
    fun setUp() {
        loadEnv()
        // Override ports with random available ports to avoid conflicts with running local app
        val wmPort = findFreePort()
        val apiPort = findFreePort()
        val upstreamPort = findFreePort()
        
        env.set(EnvVar.WireMockServerPort.key, wmPort.toString())
        env.set(EnvVar.KtorApiPort.key, apiPort.toString())
        env.set(EnvVar.DynAllowedPorts.key, "$upstreamPort,80,443")

        upstream = WireMockServer(
            options()
                .port(upstreamPort)
                .notifier(Slf4jNotifier(false))
                .disableRequestJournal()
        )
        upstream.start()

        upstreamServiceName = "testInstanceOfWireMockServer"
        env.set(EnvVar.ServiceMap.key, "$upstreamServiceName=${upstreamBaseUrl()}")

        // Start proxy (WireMock ingress)
        // Start proxy (WireMock ingress)
        // Start proxy (WireMock ingress)
        val cfg = AppConfigLoader.load()
        deps = buildDependencies(cfg)
        
        // Use Fake Stub Repository for tests (avoid requiring DDB)
        val fakeRepo = helpers.FakeStubRepository()
        val fakeSessionRepo = helpers.FakeSessionRepository()
        
        val testStubService = se.strawberry.service.stub.StubServiceImpl(
            se.strawberry.common.Json.mapper, 
            deps.wireMockClient, 
            fakeRepo
        )
        // Also inject FakeSessionRepository into deps so DynamicRoutingGuard uses it
        deps = deps.copy(
            stubService = testStubService,
            sessionRepository = fakeSessionRepo
        )

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
    }

    protected fun createSession(id: String) {
        val session = se.strawberry.repository.session.SessionRepository.Session(
            id = id,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000, // 1 hour
            status = se.strawberry.repository.session.SessionRepository.Session.Status.ACTIVE
        )
        deps.sessionRepository.create(session)
    }

    protected fun call(sessionId: String?, path: String): okhttp3.Response {
        val req = Request.Builder()
            .url("${proxyBaseUrl()}$path")
            .addHeader("X-Mock-Target-Service", upstreamServiceName)
            .apply { if (sessionId != null) addHeader("X-Mock-Session-Id", sessionId) }
            .build()
        return http.newCall(req)
            .execute()
    }

    protected fun proxyBaseUrl(): String = "http://localhost:${proxy.port()}"
    protected fun upstreamBaseUrl(): String = "http://localhost:${upstream.port()}"
    protected fun apiBaseUrl(): String = "http://localhost:${Env.int(EnvVar.KtorApiPort)}"
    private fun findFreePort(): Int { ServerSocket(0).use { return it.localPort } }

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
}