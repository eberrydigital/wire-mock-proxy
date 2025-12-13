package se.strawberry.app

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import se.strawberry.api.handlers.RequestsHandler
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.domain.stub.CreateStubRequest
import se.strawberry.repository.session.InMemorySessionRepository
import se.strawberry.repository.session.SessionRepository
import java.util.*
import com.github.tomakehurst.wiremock.http.Response as WMResponse

val DependenciesKey = AttributeKey<AppDependencies>("AppDependencies")

fun Application.installDependencies() {
    attributes.put(DependenciesKey, buildDependencies())
}

fun Application.dependencies(): AppDependencies = attributes[DependenciesKey]

fun Application.mockGateway() {
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { jackson() }
    val dependencies = dependencies()

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
                val mapper = dependencies.mapper

                val dto = try {
                    mapper.readValue(body, CreateStubRequest::class.java)
                } catch (_: Exception) {
                    call.respondBadRequest("invalid_json")
                    return@post
                }

                val sessionId = call.sessionIdOrNull()
                if (sessionId == null) {
                    call.respondBadRequest("missing_session")
                    return@post
                }

                val wmResp = dependencies.stubService.create(dto, sessionId)
                respondFromWireMock(call, wmResp)
            }

            // List stubs
            get {
                val wmResp = dependencies.stubService.list()
                respondFromWireMock(call, wmResp)
            }

            // Delete by id
            delete("/{id}") {
                val id = call.parameters["id"]?.trim().orEmpty()
                if (id.isEmpty()) {
                    call.respondBadRequest("missing_id")
                    return@delete
                }

                val wmResp = dependencies.stubService.delete(id)
                respondFromWireMock(call, wmResp)
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

private fun ApplicationCall.sessionIdOrNull(): String? =
    request.headers[Headers.X_MOCK_SESSION_ID]?.trim()?.takeIf { it.isNotEmpty() }

private suspend fun ApplicationCall.respondBadRequest(reason: String) {
    respondText(
        """{"error":"bad_request","reason":"$reason"}""",
        ContentType.Application.Json,
        HttpStatusCode.BadRequest
    )
}
