package se.strawberry.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import se.strawberry.api.models.ErrorResponse

/**
 * Extension functions for Ktor ApplicationCall to simplify response handling
 * with standardized models.
 */

/**
 * Respond with a successful response and data.
 *
 * @param data The response data to send
 * @param status The HTTP status code (default: OK)
 */
suspend inline fun <reified T : Any> ApplicationCall.respondSuccess(
    data: T,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respond(status, data)
}

/**
 * Respond with a standardized error response.
 *
 * @param status The HTTP status code
 * @param error The error type/code
 * @param reason Optional specific reason
 * @param message Optional human-readable message
 */
suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    error: String,
    reason: String? = null,
    message: String? = null
) {
    respond(status, ErrorResponse(error, reason, message))
}

/**
 * Respond with a Bad Request (400) error.
 *
 * @param reason Specific reason for the bad request
 * @param message Optional human-readable message
 */
suspend fun ApplicationCall.respondBadRequest(
    reason: String,
    message: String? = null
) = respondError(
    status = HttpStatusCode.BadRequest,
    error = "bad_request",
    reason = reason,
    message = message
)

/**
 * Respond with a Not Found (404) error.
 *
 * @param reason Specific reason for not found
 * @param message Optional human-readable message
 */
suspend fun ApplicationCall.respondNotFound(
    reason: String = "not_found",
    message: String? = null
) = respondError(
    status = HttpStatusCode.NotFound,
    error = "not_found",
    reason = reason,
    message = message
)

/**
 * Respond with an Internal Server Error (500).
 *
 * @param reason Specific reason for the error
 * @param message Optional human-readable message
 */
suspend fun ApplicationCall.respondInternalError(
    reason: String = "internal_error",
    message: String? = null
) = respondError(
    status = HttpStatusCode.InternalServerError,
    error = "internal_server_error",
    reason = reason,
    message = message
)

/**
 * Respond with an Unauthorized (401) error.
 *
 * @param reason Specific reason for unauthorized access
 * @param message Optional human-readable message
 */
suspend fun ApplicationCall.respondUnauthorized(
    reason: String = "unauthorized",
    message: String? = null
) = respondError(
    status = HttpStatusCode.Unauthorized,
    error = "unauthorized",
    reason = reason,
    message = message
)

