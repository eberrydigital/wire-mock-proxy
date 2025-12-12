package api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import se.strawberry.common.Json
import se.strawberry.domain.stub.CreateStubRequest

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
        stub: CreateStubRequest,
        sessionId: String? = null
    ): Response {
        val body = Json.mapper.writeValueAsString(stub).toRequestBody(JSON)

        val req = Request.Builder()
            .url("$proxyBaseUrl/_proxy-api/stubs")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Mock-Target-Service", targetService)
            .apply { if (sessionId != null) addHeader("X-Mock-Session-Id", sessionId) }
            .post(body)
            .build()

        return client.newCall(req).execute()
    }
}
