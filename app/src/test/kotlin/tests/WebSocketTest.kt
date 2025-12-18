package tests

import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import se.strawberry.config.Env
import se.strawberry.config.EnvVar
import tests.setup.BaseTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebSocketTest : BaseTest() {

    @Test
    fun `should receive broadcasted traffic via websocket`() {
        val sessionId = "ws-session-1"
        createSession(sessionId)
        val endpoint = "/api/ws-test"
        
        // Connect to WS
        val latch = CountDownLatch(1)
        val messages = mutableListOf<String>()
        
        val wsUrl = "ws://localhost:${Env.int(EnvVar.KtorApiPort)}/_proxy-api/ws/traffic"
        val request = okhttp3.Request.Builder().url(wsUrl).build()
        
        val wsListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                messages.add(text)
                // We expect at least one message with our sessionId
                if (text.contains(sessionId)) {
                    latch.countDown()
                }
            }
        }
        
        val ws = http.newWebSocket(request, wsListener)
        
        // Wait for connection (optional, but good practice)
        Thread.sleep(500)
        
        // Send traffic
        upstream.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(endpoint))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(200)))
            
        call(sessionId, endpoint).close()
        
        // Verify receipt
        val received = latch.await(5, TimeUnit.SECONDS)
        assertThat("Should have received traffic event", received, `is`(true))
        assertThat(messages.size, greaterThanOrEqualTo(1))
        assertThat(messages[0], containsString(sessionId))
        assertThat(messages[0], containsString(endpoint))
        
        ws.close(1000, "done")
    }

    @Test
    fun `should filter traffic by sessionId`() {
        val targetSession = "ws-filter-target"
        val otherSession = "ws-filter-ignored"
        createSession(targetSession)
        createSession(otherSession)
        val endpoint = "/api/ws-filter"
        
        val messages = mutableListOf<String>()
        val latch = CountDownLatch(1)
        val wsUrl = "ws://localhost:${Env.int(EnvVar.KtorApiPort)}/_proxy-api/ws/traffic"
        
        val wsListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                messages.add(text)
                if (text.contains(targetSession)) {
                    latch.countDown()
                }
            }
        }
        
        val ws = http.newWebSocket(okhttp3.Request.Builder().url(wsUrl).build(), wsListener)
        
        // Send filter command
        val filterCmd = """{"sessionId": "$targetSession"}"""
        ws.send(filterCmd)
        
        Thread.sleep(500) // Allow filter to apply
        
        // Send traffic for OTHER session
        upstream.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(com.github.tomakehurst.wiremock.client.WireMock.urlMatching(".*"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(200)))

        call(otherSession, endpoint).close()
        Thread.sleep(500) // Wait to ensure it wasn't received
        
        // Send traffic for TARGET session
        call(targetSession, endpoint).close()
        
        val received = latch.await(5, TimeUnit.SECONDS)
        assertThat("Should have received target session traffic", received, `is`(true))
        
        // Verify we ONLY got the target session traffic (plus maybe the initial connection/filter ack if any, but we don't ACK)
        // Check for error response
        assertThat(messages.toString(), not(containsString("error")))
        
        // Let's check that NONE of the messages contain otherSession
        val leaked = messages.any { it.contains(otherSession) }
        assertThat("Should not receive other session traffic", leaked, `is`(false))
        
        ws.close(1000, "done")
    }

    @Test
    fun `should reject closed session filter`() {
        val sessionId = "ws-closed-session"
        createSession(sessionId)
        
        // Close the session
        deps.sessionRepository.close(sessionId)
        
        val messages = mutableListOf<String>()
        val closeLatch = CountDownLatch(1)
        val wsUrl = "ws://localhost:${Env.int(EnvVar.KtorApiPort)}/_proxy-api/ws/traffic"
        
        val wsListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                messages.add(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                closeLatch.countDown()
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closeLatch.countDown()
            }
        }
        
        val ws = http.newWebSocket(okhttp3.Request.Builder().url(wsUrl).build(), wsListener)
        
        Thread.sleep(500) // Wait for connection
        
        // Try to set filter to closed session
        val filterCmd = """{"sessionId": "$sessionId"}"""
        ws.send(filterCmd)
        
        // Wait for connection to close
        val closed = closeLatch.await(5, TimeUnit.SECONDS)
        assertThat("WebSocket should have closed", closed, `is`(true))
        
        // Verify error message was sent before closing
        val errorMsg = messages.firstOrNull { it.contains("error") }
        assertThat("Error message should exist", errorMsg, notNullValue())
        assertThat("Error should indicate session is closed", errorMsg, containsString("session_closed"))
    }
}
