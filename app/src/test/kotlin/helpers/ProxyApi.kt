package helpers

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import se.strawberry.api.Endpoints
import se.strawberry.common.Headers.X_MOCK_SESSION_ID
import se.strawberry.common.Headers.X_MOCK_TARGET_SERVICE
import se.strawberry.common.Json
import se.strawberry.api.models.stub.CreateStubRequest

/**
 * Utility for executing _proxy-api calls
 */

object ProxyApi {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Create a stub via Proxy API
     * @param sessionId if null — will create a global stub, otherwise a session-scoped stub
     */
    fun createStub(
        client: OkHttpClient,
        apiBaseUrl: String,
        targetService: String,
        stub: CreateStubRequest,
        sessionId: String? = null
    ): Response {
        val body = Json.mapper.writeValueAsString(stub).toRequestBody(JSON)

        val req = Request.Builder()
            .url("$apiBaseUrl${Endpoints.Paths.STUBS}")
            .addHeader("Content-Type", "application/json")
            .addHeader(X_MOCK_TARGET_SERVICE, targetService)
            .apply { if (sessionId != null) addHeader(X_MOCK_SESSION_ID, sessionId) }
            .post(body)
            .build()

        return client.newCall(req).execute()
    }
}
