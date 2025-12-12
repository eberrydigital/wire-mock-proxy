package se.strawberry.app

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import se.strawberry.admin.ServerRef
import se.strawberry.api.handlers.SessionScope
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.common.MetadataKeys
import se.strawberry.domain.stub.CreateStubRequest
import se.strawberry.stubs.dto.StubBuilder

/**
 * RK1/RK2/RK3: Ktor Application scaffold
 * - /_proxy-api/health
 * - Stubs API: POST/GET/DELETE /_proxy-api/stubs
 * - Reverse proxy placeholder (RK2) via installReverseProxy(), not wired into Main yet.
 */
fun Application.mockGateway() {
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { jackson() }

    routing {
        // Health
        get("/_proxy-api/health") {
            call.respondText("{\"status\":\"ok\"}", ContentType.Application.Json, HttpStatusCode.OK)
        }

        // RK3: Stubs API
        route("/_proxy-api/stubs") {
            // Create stub
            post {
                val body = call.receiveText()
                val mapper = Json.mapper
                val dtoOriginal = mapper.readValue(body, CreateStubRequest::class.java)
                val sessionId = call.request.headers[Headers.X_MOCK_SESSION_ID]?.trim()?.takeIf { it.isNotEmpty() }
                val patchedDto = SessionScope.withSessionMatch(dtoOriginal, sessionId)
                val stub = StubBuilder.buildStubMapping(patchedDto)
                ServerRef.server.addStubMapping(stub)

                val md = stub.metadata
                val usesLeft: Int? = md?.let { (it[MetadataKeys.REMAINING_USES] as? Number)?.toInt() }
                val expiresAt: Long? = md?.let { (it[MetadataKeys.EXPIRES_AT] as? Number)?.toLong() }

                val payload = mapper.writeValueAsString(
                    mapOf(
                        "id" to stub.id,
                        "summary" to "${dtoOriginal.request.method} ${dtoOriginal.request.url.value}",
                        "usesLeft" to usesLeft,
                        MetadataKeys.EXPIRES_AT to expiresAt
                    )
                )
                call.respondText(payload, ContentType.Application.Json, HttpStatusCode.Created)
            }

            // List stubs
            get {
                val mapper = Json.mapper
                val list = ServerRef.server.listAllStubMappings().mappings.map { sm ->
                    val md = sm.metadata
                    mapOf(
                        "id" to sm.id,
                        "priority" to sm.priority,
                        "request" to sm.request?.url,
                        "method" to sm.request?.method?.value(),
                        "usesLeft" to md?.let { (it[MetadataKeys.REMAINING_USES] as? Number)?.toInt() },
                        MetadataKeys.EXPIRES_AT to md?.let { (it[MetadataKeys.EXPIRES_AT] as? Number)?.toLong() }
                    )
                }
                call.respond(mapper.writeValueAsString(list))
            }

            // Delete by id
            delete("/{id}") {
                val id = call.parameters["id"]
                val mapper = Json.mapper
                if (id.isNullOrBlank()) {
                    call.respondText("{\"error\":\"bad_request\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@delete
                }
                val sm = ServerRef.server
                    .listAllStubMappings()
                    .mappings
                    .firstOrNull { it.id.equals(id) }
                if (sm == null) {
                    call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                } else {
                    ServerRef.server.removeStubMapping(sm)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        // RK2: Reverse proxy placeholder (disabled until RK7)
        // val proxy = ReverseProxy(internalBaseUrl = "http://127.0.0.1:9090")
        // route("/{...}") { handle { proxy.forward(call) } }
    }
}
