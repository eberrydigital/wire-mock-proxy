package se.strawberry.api.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.Response
import se.strawberry.admin.ServerRef
import se.strawberry.common.Headers
import se.strawberry.common.MetadataKeys
import se.strawberry.config.EnvironmentConfig
import se.strawberry.stubs.dto.CreateStubRequest
import se.strawberry.stubs.dto.HeaderMatch
import se.strawberry.stubs.dto.HeaderMatchType
import se.strawberry.stubs.dto.StubBuilder

class StubsHandler(
    private val mapper: ObjectMapper,
) {
    fun create(request: Request): Response {
        val dtoOriginal: CreateStubRequest = mapper.readValue(request.bodyAsString)
        val sessionId = request.getHeader(Headers.X_MOCK_SESSION_ID)?.trim()?.takeIf { it.isNotEmpty() }
        val patchedDto: CreateStubRequest = if (sessionId != null) {
            val existing = dtoOriginal.request.headers
            val patchedHeaders = existing + (Headers.X_MOCK_SESSION_ID to HeaderMatch(
                type = HeaderMatchType.EQUAL_TO,
                value = sessionId
            ))
            dtoOriginal.copy(
                request = dtoOriginal.request.copy(headers = patchedHeaders)
            )
        } else dtoOriginal
        val proxyTarget = EnvironmentConfig.proxyTarget
        val stub = StubBuilder.buildStubMapping(patchedDto, proxyTarget)
        ServerRef.server.addStubMapping(stub)

        val md = stub.metadata
        val usesLeft: Int? = md?.let { (it[MetadataKeys.REMAINING_USES] as? Number)?.toInt() }
        val expiresAt: Long? = md?.let { (it[MetadataKeys.EXPIRES_AT] as? Number)?.toLong() }

        val payload = mapper.writeValueAsString(
            mapOf(
                "id" to stub.id,
                "summary" to "${dtoOriginal.request.method} ${dtoOriginal.request.url.value}",
                "usesLeft" to usesLeft,
                MetadataKeys.EXPIRES_AT to expiresAt
            )
        )
        return json(201, payload)
    }

    fun list(): Response {
        val list = ServerRef.server.listAllStubMappings().mappings.map { sm ->
            val md = sm.metadata
            mapOf(
                "id" to sm.id,
                "priority" to sm.priority,
                "request" to sm.request?.url,
                "method" to sm.request?.method?.value(),
                "usesLeft" to md?.let { (it[MetadataKeys.REMAINING_USES] as? Number)?.toInt() },
                MetadataKeys.EXPIRES_AT to md?.let { (it[MetadataKeys.EXPIRES_AT] as? Number)?.toLong() }
            )
        }
        return json(200, mapper.writeValueAsString(list))
    }

    fun delete(id: String): Response {
        val sm = ServerRef.server
            .listAllStubMappings()
            .mappings
            .firstOrNull { it.id.equals(id) }
            ?: return json(404, """{"error":"not_found"}""")

        ServerRef.server.removeStubMapping(sm)
        return Response.response()
            .status(204)
            .build()
    }

    private fun json(code: Int, body: String): Response =
        Response.response()
            .status(code)
            .headers(HttpHeaders(HttpHeader.httpHeader(Headers.CONTENT_TYPE, Headers.JSON)))
            .body(body)
            .build()
}
