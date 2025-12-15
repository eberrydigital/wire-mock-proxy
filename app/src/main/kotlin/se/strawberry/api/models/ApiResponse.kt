package se.strawberry.api.models

/**
 * Base sealed class for API responses.
 * Allows type-safe handling of success and error cases.
 *
 * This can be used for future enhancements like:
 * - Consistent wrapper around all responses
 * - Type-safe error handling
 * - Middleware processing
 *
 * @param T The type of data in successful response
 */
sealed class ApiResponse<out T> {
    /**
     * Successful API response containing data
     * @property data The actual response data
     */
    data class Success<T>(val data: T) : ApiResponse<T>()

    /**
     * Error API response containing error details
     * @property error The error information
     */
    data class Error(val error: ErrorResponse) : ApiResponse<Nothing>()
}

