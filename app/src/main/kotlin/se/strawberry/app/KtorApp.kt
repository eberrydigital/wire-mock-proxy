package se.strawberry.app

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.config.AppConfig
import se.strawberry.domain.stub.CreateStubRequest
import se.strawberry.repository.session.SessionRepository
import java.util.*
import com.github.tomakehurst.wiremock.http.Response as WMResponse

val DependenciesKey = AttributeKey<AppDependencies>("AppDependencies")

fun Application.installDependencies(cfg: AppConfig) {
    attributes.put(DependenciesKey, buildDependencies(cfg))
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
                val queryParameters = call.request.queryParameters
                val query: Map<String, String> = queryParameters.names().associateWith { name -> queryParameters.getAll(name)?.lastOrNull() ?: "" }
                val resp: WMResponse = dependencies.requestService.list(query)
                respondFromWireMock(call, resp)
            }
            // Get by id
            get("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respondBadRequest("missing_id")
                } else {
                    val resp: WMResponse = dependencies.requestService.byId(id)
                    respondFromWireMock(call, resp)
                }
            }
            // Clear
            delete {
                val resp: WMResponse = dependencies.requestService.clear()
                respondFromWireMock(call, resp)
            }
            // Export NDJSON
            get("/export") {
                val resp: WMResponse = dependencies.requestService.export()
                respondFromWireMock(call, resp)
            }
        }

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
                dependencies.sessionRepository.create(s)
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
                val s = id?.let { dependencies.sessionRepository.get(it) }
                if (s == null) {
                    call.respondNotFound(reason = "not_found", message = "Session not found")
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
                    call.respondBadRequest(reason = "bad_request", message = "Missing session id")
                } else {
                    val ok = dependencies.sessionRepository.close(id)
                    if (!ok) {
                        call.respondBadRequest(reason = "not_found", message = "Session not found")
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


private suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    error: String,
    reason: String? = null,
    message: String? = null
) {
    val payload = buildString {
        append("""{"error":"$error"""")
        if (reason != null) append(""","reason":"$reason"""")
        if (message != null) append(""","message":"$message"""")
        append("}")
    }

    respondText(payload, ContentType.Application.Json, status)
}

private suspend fun ApplicationCall.respondBadRequest(reason: String, message: String? = null) =
    respondError(HttpStatusCode.BadRequest, error = "bad_request", reason = reason, message = message)

private suspend fun ApplicationCall.respondNotFound(reason: String = "not_found", message: String? = null) =
    respondError(HttpStatusCode.NotFound, error = "not_found", reason = reason, message = message)

