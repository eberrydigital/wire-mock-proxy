package tests.setup

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.netty.NettyApplicationEngine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import se.strawberry.app.ServerBootstrap
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import org.junit.jupiter.api.extension.ExtendWith
import se.strawberry.app.KtorBootstrap
import se.strawberry.app.buildDependencies
import se.strawberry.config.AppConfigLoader
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
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
    protected var apiPort: Int = 0

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
        upstream = WireMockServer(
            options()
                .dynamicPort()
                .notifier(Slf4jNotifier(false))
                .disableRequestJournal()
        )
        upstream.start()

        upstreamServiceName = "testInstanceOfWireMockServer"

        val proxyPort = freePort()
        apiPort = freePort()

        // ENV for both servers
        env.set("SERVICE_MAP", "$upstreamServiceName=${upstreamBaseUrl()}")
        env.set("DYN_ALLOWED_PORTS",
            upstream.port()
                .toString()
        )
        env.set("PORT", proxyPort.toString())
        env.set("API_PORT", apiPort.toString())

        // Start proxy (WireMock ingress)
        proxy = ServerBootstrap.start()

        // Start Ktor API
        val cfg = AppConfigLoader.load()
        val deps = buildDependencies()
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


    fun call(sessionId: String?, endpoint: String): Response {
        val req = Request.Builder()
            .url("${proxyBaseUrl()}$endpoint")
            .addHeader("X-Mock-Target-Service", upstreamServiceName)
            .apply { if (sessionId != null) addHeader("X-Mock-Session-Id", sessionId) }
            .build()
        return http.newCall(req)
            .execute()
    }

    protected fun proxyBaseUrl(): String = "http://localhost:${proxy.port()}"
    protected fun upstreamBaseUrl(): String = "http://localhost:${upstream.port()}"
    protected fun apiBaseUrl(): String = "http://localhost:$apiPort"
}

private fun freePort(): Int = ServerSocket(0).use { it.localPort }