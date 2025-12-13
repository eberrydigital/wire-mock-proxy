package se.strawberry.service.stub

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Response
import se.strawberry.common.Headers
import se.strawberry.common.MetadataKeys
import se.strawberry.domain.stub.CreateStubRequest
import se.strawberry.helpers.SessionHelper
import se.strawberry.service.wiremock.WireMockClient
import se.strawberry.stubs.dto.StubBuilder


class StubServiceImpl(
    private val mapper: ObjectMapper,
    private val wireMockClient: WireMockClient
) : StubService {
    override fun create(dto: CreateStubRequest, sessionId: String): Response {
        val patchedDto = SessionHelper.withSessionMatch(dto, sessionId)
        val stub = StubBuilder.buildStubMapping(patchedDto)
        wireMockClient.addStub(stub)


        val md = stub.metadata
        val usesLeft: Int? = md?.let { (it[MetadataKeys.REMAINING_USES] as? Number)?.toInt() }
        val expiresAt: Long? = md?.let { (it[MetadataKeys.EXPIRES_AT] as? Number)?.toLong() }

        val payload = mapper.writeValueAsString(
            mapOf(
                "id" to stub.id,
                "summary" to "${dto.request.method} ${dto.request.url.value}",
                "usesLeft" to usesLeft,
                MetadataKeys.EXPIRES_AT to expiresAt
            )
        )
        return json(201, payload)
    }

    override fun list(): Response {
        val list = wireMockClient.listStubs().map { sm ->
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

    override fun delete(id: String): Response {
        val stub = wireMockClient.listStubs()
            .firstOrNull { it.id.toString().equals(id) } ?: return json(404, """{"error":"not_found"}""")

        wireMockClient.removeStub(stub)
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