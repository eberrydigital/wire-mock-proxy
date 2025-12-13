package se.strawberry.app

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import se.strawberry.admin.ServerRef
import se.strawberry.api.handlers.RequestsHandler
import se.strawberry.helpers.SessionHelper
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.common.MetadataKeys
import se.strawberry.domain.stub.CreateStubRequest
import se.strawberry.stubs.dto.StubBuilder
import com.github.tomakehurst.wiremock.http.Response as WMResponse
import se.strawberry.repository.session.InMemorySessionRepository
import se.strawberry.repository.session.SessionRepository
import java.util.UUID

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
                val patchedDto = SessionHelper.withSessionMatch(dtoOriginal, sessionId)
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

        // RK4: Requests API
        route("/_proxy-api/requests") {
            // List
            get {
                val mapper = Json.mapper
                val handler = RequestsHandler(mapper)
                val qp = call.request.queryParameters
                val query: Map<String, String> = qp.names().associateWith { name -> qp.getAll(name)?.lastOrNull() ?: "" }
                val resp: WMResponse = handler.list(query)
                respondFromWireMock(call, resp)
            }
            // Get by id
            get("/{id}") {
                val mapper = Json.mapper
                val handler = RequestsHandler(mapper)
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respondText("{\"error\":\"bad_request\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                } else {
                    val resp: WMResponse = handler.byId(id)
                    respondFromWireMock(call, resp)
                }
            }
            // Clear
            delete {
                val mapper = Json.mapper
                val handler = RequestsHandler(mapper)
                val resp: WMResponse = handler.clear()
                respondFromWireMock(call, resp)
            }
            // Export NDJSON
            get("/export") {
                val mapper = Json.mapper
                val handler = RequestsHandler(mapper)
                val resp: WMResponse = handler.export()
                respondFromWireMock(call, resp)
            }
        }

        // RK5: Sessions API
        val sessionsRepo: SessionRepository = InMemorySessionRepository()
        route("/_proxy-api/sessions") {
            // Create session
            post {
                val id = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val s = SessionRepository.Session(
                    id = id,
                    name = null,
                    owner = null,
                    createdAt = now,
                    expiresAt = null,
                    status = SessionRepository.Session.Status.ACTIVE
                )
                sessionsRepo.create(s)
                val payload = Json.mapper.writeValueAsString(
                    mapOf(
                        "id" to s.id,
                        "status" to s.status.name,
                        "createdAt" to s.createdAt,
                        "expiresAt" to s.expiresAt
                    )
                )
                call.respondText(payload, ContentType.Application.Json, HttpStatusCode.Created)
            }
            // Get session by id
            get("/{id}") {
                val id = call.parameters["id"]
                val s = id?.let { sessionsRepo.get(it) }
                if (s == null) {
                    call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                } else {
                    val payload = Json.mapper.writeValueAsString(
                        mapOf(
                            "id" to s.id,
                            "status" to s.status.name,
                            "createdAt" to s.createdAt,
                            "expiresAt" to s.expiresAt
                        )
                    )
                    call.respondText(payload, ContentType.Application.Json)
                }
            }
            // Close session
            post("/{id}/close") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respondText("{\"error\":\"bad_request\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                } else {
                    val ok = sessionsRepo.close(id)
                    if (!ok) {
                        call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                    } else {
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}

private suspend fun respondFromWireMock(call: ApplicationCall, wm: WMResponse) {
    val status = HttpStatusCode.fromValue(wm.status)
    val body = wm.bodyAsString ?: ""
    val contentType = wm.headers?.getHeader(Headers.CONTENT_TYPE)?.takeIf { it.isPresent }?.firstValue()
    if (contentType != null) {
        // If content-type present, use respondText with that content type
        call.respondText(body, ContentType.parse(contentType), status)
    } else {
        // Fallback to bytes
        call.respondBytes(body.toByteArray(), status = status)
    }
}
