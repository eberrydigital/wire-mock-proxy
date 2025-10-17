package api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

object ProxyApi {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Create a stub via Proxy API
     * @param sessionId if null — will create a global stub, otherwise a session-scoped stub
     */
    fun createStub(
        client: OkHttpClient,
        proxyBaseUrl: String,
        targetService: String,
        stubJsonBody: String,
        sessionId: String? = null
    ): Response {
        val req = Request.Builder()
            .url("$proxyBaseUrl/_proxy-api/stubs")
            .addHeader("Content-Type", "application/json")
            .post(stubJsonBody.toRequestBody(JSON))
            .addHeader("X-Mock-Target-Service", targetService)
            .apply {
                if (sessionId != null) addHeader("X-Mock-Session-Id", sessionId)
            }
            .build()
        return client.newCall(req).execute()
    }
}
