package helpers

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import se.strawberry.api.Endpoints
import se.strawberry.api.models.sessions.SessionsCloseRequestModel
import se.strawberry.common.Json

/**
 * Utility for working with Sessions API
 */
object SessionApi {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Create a new session
     */
    fun createSession(
        client: OkHttpClient,
        apiBaseUrl: String,
        name: String? = null,
        owner: String? = null
    ): Response {
        val body = Json.mapper.writeValueAsString(
            buildMap {
                name?.let { put("name", it) }
                owner?.let { put("owner", it) }
            }
        ).toRequestBody(JSON)

        val req = Request.Builder()
            .url("$apiBaseUrl${Endpoints.Paths.SESSIONS}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        return client.newCall(req).execute()
    }

    /**
     * Get session by ID
     */
    fun getSession(
        client: OkHttpClient,
        apiBaseUrl: String,
        sessionId: String
    ): Response {
        val req = Request.Builder()
            .url("$apiBaseUrl${Endpoints.Paths.SESSIONS}/$sessionId")
            .get()
            .build()

        return client.newCall(req).execute()
    }

    /**
     * Close session by ID
     */
    fun closeSession(
        client: OkHttpClient,
        apiBaseUrl: String,
        sessionId: String
    ): Response {
        val body = SessionsCloseRequestModel(sessionId).toJsonBody()
        val req = Request.Builder()
            .url("$apiBaseUrl${Endpoints.Paths.SESSIONS}/close")
            .patch(body)
            .build()

        return client.newCall(req).execute()
    }
}

/**
 * Data class for session response
 */
data class SessionResponse(
    val id: String,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long?
)

