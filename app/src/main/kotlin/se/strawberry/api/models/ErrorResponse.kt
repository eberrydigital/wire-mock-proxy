package se.strawberry.api.models

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Standardized error response model for all API endpoints.
 *
 * @property error The error type/code (e.g., "bad_request", "not_found")
 * @property reason Optional specific reason for the error
 * @property message Optional human-readable error message
 * @property timestamp Unix timestamp when the error occurred
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val error: String,
    val reason: String? = null,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

