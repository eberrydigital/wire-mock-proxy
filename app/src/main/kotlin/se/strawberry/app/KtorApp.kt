package se.strawberry.app

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * RK1: Minimal Ktor Application scaffold with /_proxy-api/health and route placeholders.
 * Note: Not wired into Main yet; cutover will happen in RK7.
 */
fun Application.mockGateway() {

    routing {
        // Health
        get("/_proxy-api/health") { call.respondText("{\"status\":\"ok\"}", ContentType.Application.Json, HttpStatusCode.OK) }

        // Placeholders for future routes (RK3–RK5)
        // stubs: POST/GET/DELETE /_proxy-api/stubs
        // requests: GET /_proxy-api/requests, GET /_proxy-api/requests/{id}, DELETE /_proxy-api/requests, GET /_proxy-api/requests/export
        // sessions: POST /_proxy-api/sessions, GET /_proxy-api/sessions/{id}, POST /_proxy-api/sessions/{id}/close
    }
}

