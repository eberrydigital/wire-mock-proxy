package se.strawberry.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.utils.io.core.toByteArray

/**
 * RK2: Minimal reverse proxy utility (not wired yet).
 * Forwards a request from Ktor to an internal WireMock base URL.
 * Currently supports GET; TODO: add non-GET with body streaming.
 */
class ReverseProxy(private val internalBaseUrl: String) {
    private val client = HttpClient(Java) {
        expectSuccess = false
    }

    suspend fun forward(call: ApplicationCall) {
        val path = call.request.path()
        val query = call.request.queryString()
        val targetUrl = if (query.isNotEmpty()) "$internalBaseUrl$path?$query" else "$internalBaseUrl$path"

        val resp: HttpResponse = client.get(targetUrl) {
            // copy headers except Host
            headers {
                call.request.headers.forEach { name, values ->
                    if (!name.equals(HttpHeaders.Host, true)) {
                        values.forEach { v -> append(name, v) }
                    }
                }
            }
        }

        val contentType = resp.headers[HttpHeaders.ContentType]
        val bodyText = resp.bodyAsText()
        if (contentType != null) {
            call.respondText(bodyText, contentType = io.ktor.http.ContentType.parse(contentType), status = resp.status)
        } else {
            call.respondBytes(bodyText.toByteArray(), status = resp.status)
        }
    }
}
