package se.strawberry.wiremock

import com.fasterxml.jackson.databind.JsonNode
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.Metadata
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.matching.UrlPattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import se.strawberry.api.models.stub.BodyMatchMode
import se.strawberry.api.models.stub.BodyMatcherType
import se.strawberry.api.models.stub.CreateStubRequest
import se.strawberry.api.models.stub.HeaderMatchType
import se.strawberry.api.models.stub.ReqMatchMethods
import se.strawberry.api.models.stub.RespMode
import se.strawberry.api.models.stub.UrlMatchType
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.common.MatcherNames
import se.strawberry.common.MetadataKeys
import se.strawberry.common.TemplateNames

object StubBuilder {
    private val mapper = Json.mapper

    fun buildStubMapping(dto: CreateStubRequest): StubMapping {
        val mappingBuilder = when (dto.request.url.type) {
            UrlMatchType.EXACT -> requestMatching(method = dto.request.method, url = WireMock.urlEqualTo(dto.request.url.value))
            UrlMatchType.LOOSENED -> requestMatching(method = dto.request.method, url = WireMock.urlMatching(dto.request.url.value))
        }

        dto.request.headers.forEach { (name, headerMatch) ->
            val headerValue = when (headerMatch.type) {
                HeaderMatchType.EQUAL_TO -> WireMock.equalTo(headerMatch.value)
                HeaderMatchType.MATCHES -> WireMock.matching(headerMatch.value)
                HeaderMatchType.CONTAINS -> WireMock.containing(headerMatch.value)
            }
            mappingBuilder.withHeader(name, headerValue)
        }

        dto.request.body?.let { bodyMatch ->
            when (bodyMatch.mode) {
                BodyMatchMode.JSON -> bodyMatch.matchers.forEach { matcher ->
                    when (matcher.type) {
                        BodyMatcherType.JSON_PATH -> mappingBuilder.withRequestBody(WireMock.matchingJsonPath(matcher.expr ?: "$"))

                        BodyMatcherType.EQUAL_TO_JSON -> {
                            val jsonNode = when (val v = matcher.value) {
                                is JsonNode -> v
                                is String -> mapper.readTree(v)
                                null -> mapper.nullNode()
                                else -> mapper.valueToTree(v)
                            }
                            mappingBuilder.withRequestBody(
                                WireMock.equalToJson(
                                    jsonNode.toString(),
                                    matcher.ignoreArrayOrder ?: true,
                                    matcher.ignoreExtraElements ?: true
                                )
                            )
                        }

                        BodyMatcherType.MATCHES -> mappingBuilder.withRequestBody(WireMock.matching(matcher.value.toString()))
                        BodyMatcherType.CONTAINS -> mappingBuilder.withRequestBody(WireMock.containing(matcher.value.toString()))
                    }
                }

                BodyMatchMode.TEXT -> bodyMatch.matchers.forEach { matcher ->
                    when (matcher.type) {
                        BodyMatcherType.MATCHES -> mappingBuilder.withRequestBody(WireMock.matching(matcher.value.toString()))
                        BodyMatcherType.CONTAINS -> mappingBuilder.withRequestBody(WireMock.containing(matcher.value.toString()))
                        else -> {} // skip non-text matchers
                    }
                }
            }
        }

        val rb = ResponseDefinitionBuilder().withStatus(dto.response.status)
        dto.response.headers.forEach { (k, v) -> rb.withHeader(k, v) }

        when (dto.response.mode) {
            RespMode.STATIC -> {
                when {
                    dto.response.bodyJson != null -> {
                        rb.withJsonBody(mapper.valueToTree(dto.response.bodyJson))
                        if (!dto.response.headers.keys.any { it.equals("Content-Type", ignoreCase = true) }) {
                            rb.withHeader("Content-Type", "application/json")
                        }
                    }

                    dto.response.bodyText != null -> rb.withBody(dto.response.bodyText)
                }
            }

            RespMode.PATCH_UPSTREAM -> {
                rb.proxiedFrom("{{${TemplateNames.SERVICE_ORIGIN} name=request.headers.[${Headers.X_MOCK_TARGET_SERVICE}]}}")
                rb.withTransformerParameter("patch", mapper.valueToTree(dto.response.patch))
            }
        }

        var builder = mappingBuilder.atPriority(dto.priority ?: 2)
            .willReturn(rb)

        val expiresAtMs: Long? = dto.ephemeral?.ttlMs?.let { System.currentTimeMillis() + it }
        expiresAtMs?.let { builder = builder.andMatching(MatcherNames.TTL_GUARD, Parameters.one("expiresAtMs", it)) }

        val stub = builder.build()

        val md = Metadata.metadata()
            .apply {
                dto.ephemeral?.uses?.let { attr(MetadataKeys.REMAINING_USES, it) }
                dto.ephemeral?.ttlMs?.let { ttl -> attr(MetadataKeys.EXPIRES_AT, System.currentTimeMillis() + ttl) }
            }
            .build()
        stub.metadata = md

        return stub
    }

    private fun requestMatching(method: ReqMatchMethods, url: UrlPattern): MappingBuilder =
        when (method) {
            ReqMatchMethods.GET -> WireMock.get(url)
            ReqMatchMethods.POST -> WireMock.post(url)
            ReqMatchMethods.PUT -> WireMock.put(url)
            ReqMatchMethods.PATCH -> WireMock.patch(url)
            ReqMatchMethods.DELETE -> WireMock.delete(url)
            ReqMatchMethods.HEAD -> WireMock.head(url)
            ReqMatchMethods.OPTIONS -> WireMock.options(url)
            ReqMatchMethods.ANY -> WireMock.any(url)
        }
}