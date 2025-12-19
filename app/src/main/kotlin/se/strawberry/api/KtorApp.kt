package se.strawberry.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.Response as WMResponse
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import org.koin.ktor.ext.inject
import se.strawberry.api.models.health.HealthResponse
import se.strawberry.api.models.sessions.SessionsCloseRequestModel
import se.strawberry.api.models.sessions.SessionsCreateRequestModel
import se.strawberry.api.models.sessions.toResponse
import se.strawberry.api.models.stub.CreateStubRequest
import se.strawberry.common.Headers
import se.strawberry.repository.session.SessionRepository
import se.strawberry.service.request.RequestService
import se.strawberry.service.stub.StubService
import se.strawberry.service.traffic.TrafficBroadcastService
import java.util.UUID

fun Application.mockGateway() {
    install(ContentNegotiation) { jackson() }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(Headers.X_MOCK_TARGET_SERVICE)
        allowHeader(Headers.X_MOCK_SESSION_ID)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }

    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 15_000
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // ✅ Koin injected dependencies
    val mapper by inject<ObjectMapper>()
    val stubService by inject<StubService>()
    val requestService by inject<RequestService>()
    val trafficBroadcastService by inject<TrafficBroadcastService>()
    val sessionRepository by inject<SessionRepository>()

    routing {
        // Health
        get(Endpoints.Paths.HEALTH) {
            call.respond(HttpStatusCode.OK, HealthResponse())
        }

        route(Endpoints.Paths.STUBS) {
            // Create stub
            post {
                val body = call.receiveText()

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

                val wmResp = stubService.create(dto, sessionId)
                respondFromWireMock(call, wmResp)
            }

            // List stubs
            get {
                val wmResp = stubService.list()
                respondFromWireMock(call, wmResp)
            }

            // Delete by id
            delete("/{id}") {
                val id = call.parameters["id"]?.trim().orEmpty()
                if (id.isEmpty()) {
                    call.respondBadRequest("missing_id")
                    return@delete
                }

                val wmResp = stubService.delete(id)
                respondFromWireMock(call, wmResp)
            }
        }

        route(Endpoints.Paths.TRAFFIC) {
            // List
            get {
                val qp = call.request.queryParameters
                val query: Map<String, String> =
                    qp.names().associateWith { name -> qp.getAll(name)?.lastOrNull() ?: "" }

                val traffic = requestService.list(query)
                call.respond(HttpStatusCode.OK, traffic)
            }

            // Get by id
            get("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respondBadRequest("missing_id", "Request ID is required")
                    return@get
                }

                val traffic = requestService.byId(id)
                if (traffic == null) {
                    call.respondNotFound("not_found", "Request not found")
                } else {
                    call.respond(HttpStatusCode.OK, traffic)
                }
            }

            // Clear
            delete {
                requestService.clear()
                call.respond(HttpStatusCode.NoContent)
            }

            // Export NDJSON
            get("/export") {
                val ndjson = requestService.exportAsNdjson()
                call.response.header(HttpHeaders.ContentType, "application/x-ndjson")
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=\"requests.jsonl\""
                )
                call.respondText(ndjson, ContentType.parse("application/x-ndjson"), HttpStatusCode.OK)
            }
        }

        // Traffic WebSocket
        webSocket(Endpoints.Paths.WS_TRAFFIC) {
            val socketId = UUID.randomUUID().toString()

            try {
                // Register session
                trafficBroadcastService.addSession(socketId) { json ->
                    send(Frame.Text(json))
                }

                // Handle incoming messages (e.g., filter updates)
                incoming.consumeEach { frame ->
                    if (frame !is Frame.Text) return@consumeEach

                    val text = frame.readText()
                    try {
                        val node = mapper.readTree(text)
                        val sessionId = node.get("sessionId")?.asText()?.takeIf { it.isNotBlank() }

                        // Validate session if provided
                        if (sessionId != null) {
                            val session = sessionRepository.get(sessionId)
                            if (session == null) {
                                send(Frame.Text("""{"error":"invalid_session","message":"Session not found"}"""))
                                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Session not found"))
                                return@consumeEach
                            }

                            if (session.status != SessionRepository.Session.Status.ACTIVE) {
                                send(Frame.Text("""{"error":"session_closed","message":"Session is closed"}"""))
                                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Session is closed"))
                                return@consumeEach
                            }

                            val now = System.currentTimeMillis()
                            if (session.expiresAt != null && session.expiresAt < now) {
                                send(Frame.Text("""{"error":"session_expired","message":"Session has expired"}"""))
                                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Session has expired"))
                                return@consumeEach
                            }
                        }

                        trafficBroadcastService.updateFilter(socketId, sessionId)
                    } catch (_: Exception) {
                        send(Frame.Text("""{"error":"invalid_filter_json"}"""))
                    }
                }
            } finally {
                trafficBroadcastService.removeSession(socketId)
            }
        }

        route(Endpoints.Paths.SESSIONS) {
            // Create session
            post {
                val body = call.receiveText()

                val dto = try {
                    if (body.isBlank()) SessionsCreateRequestModel()
                    else mapper.readValue(body, SessionsCreateRequestModel::class.java)
                } catch (_: Exception) {
                    call.respondBadRequest("invalid_json", "Invalid request body")
                    return@post
                }

                val id = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                val session = SessionRepository.Session(
                    id = id,
                    name = dto.name,
                    owner = dto.owner,
                    createdAt = now,
                    expiresAt = dto.expiresAt,
                    status = SessionRepository.Session.Status.ACTIVE
                )

                sessionRepository.create(session)
                call.respond(HttpStatusCode.Created, session.toResponse())
            }

            // Get session by id
            get("/{id}") {
                val id = call.parameters["id"]
                val session = id?.let { sessionRepository.get(it) }
                if (session == null) {
                    call.respondNotFound(reason = "not_found", message = "Session not found")
                } else {
                    call.respond(HttpStatusCode.OK, session.toResponse())
                }
            }

            // Close session
            patch("/close") {
                val body = call.receiveText()

                val dto = try {
                    mapper.readValue(body, SessionsCloseRequestModel::class.java)
                } catch (_: Exception) {
                    call.respondBadRequest("invalid_json")
                    return@patch
                }

                if (dto.id.isBlank()) {
                    call.respondBadRequest(reason = "bad_request", message = "Missing session id")
                    return@patch
                }

                val ok = sessionRepository.close(dto.id)
                if (!ok) {
                    call.respondBadRequest(reason = "not_found", message = "Session not found")
                } else {
                    call.respond(HttpStatusCode.NoContent)
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
        call.respondText(body, ContentType.parse(contentType), status)
    } else {
        call.respondBytes(body.toByteArray(), status = status)
    }
}

private fun ApplicationCall.sessionIdOrNull(): String? =
    request.headers[Headers.X_MOCK_SESSION_ID]?.trim()?.takeIf { it.isNotEmpty() }
