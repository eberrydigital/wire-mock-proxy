package tests

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import se.strawberry.app.ServerBootstrap
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import java.util.concurrent.TimeUnit

@ExtendWith(SystemStubsExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseTest {

    protected lateinit var upstream: WireMockServer
    protected lateinit var proxy: WireMockServer
    protected lateinit var http: OkHttpClient
    protected lateinit var upstreamServiceName: String

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
        // 1) As an upstream we just use another WireMockServer that does not mock anything
        upstream = WireMockServer(  options()
            .dynamicPort()
            .notifier(Slf4jNotifier(false))
            .disableRequestJournal())
        upstream.start()

        // 2) ENV для proxy
        upstreamServiceName = "testInstanceOfWireMockServer"
        env.set("SERVICE_MAP", "$upstreamServiceName=${upstreamBaseUrl()}")
        env.set("DYN_ALLOWED_PORTS", upstream.port().toString())
        env.set("PORT", "8080")

        // 3) Here we configure the server under the test
        proxy = ServerBootstrap.start()
    }

    @AfterEach
    fun tearDown() {
        try { proxy.stop() } catch (_: Throwable) {}
        try { upstream.stop() } catch (_: Throwable) {}
    }

    protected fun proxyBaseUrl(): String = "http://localhost:${proxy.port()}"
    protected fun upstreamBaseUrl(): String = "http://localhost:${upstream.port()}"
}

