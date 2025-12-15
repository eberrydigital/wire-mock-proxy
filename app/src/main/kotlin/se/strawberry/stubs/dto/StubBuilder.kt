package se.strawberry.stubs.dto

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.head
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.options
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.common.Metadata
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.matching.UrlPattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import se.strawberry.common.Headers.X_MOCK_TARGET_SERVICE
import se.strawberry.common.Json
import se.strawberry.common.MatcherNames
import se.strawberry.common.MetadataKeys
import se.strawberry.common.TemplateNames
import se.strawberry.domain.stub.BodyMatchMode
import se.strawberry.domain.stub.BodyMatcherType
import se.strawberry.domain.stub.CreateStubRequest
import se.strawberry.domain.stub.HeaderMatchType
import se.strawberry.domain.stub.ReqMatchMethods
import se.strawberry.domain.stub.RespMode
import se.strawberry.domain.stub.UrlMatchType

object StubBuilder {
    private val mapper = Json.mapper

    fun buildStubMapping(dto: CreateStubRequest): StubMapping {
        val mappingBuilder = when (dto.request.url.type) {
            UrlMatchType.EXACT -> requestMatching(method = dto.request.method, url = urlEqualTo(dto.request.url.value))
            UrlMatchType.LOOSENED -> requestMatching(method = dto.request.method, url = urlMatching(dto.request.url.value))
        }

        dto.request.headers.forEach { (name, headerMatch) ->
            val headerValue = when (headerMatch.type) {
                HeaderMatchType.EQUAL_TO -> equalTo(headerMatch.value)
                HeaderMatchType.MATCHES -> matching(headerMatch.value)
                HeaderMatchType.CONTAINS -> containing(headerMatch.value)
            }
            mappingBuilder.withHeader(name, headerValue)
        }

        dto.request.body?.let { bodyMatch ->
            when (bodyMatch.mode) {
                BodyMatchMode.JSON -> bodyMatch.matchers.forEach { matcher ->
                    when (matcher.type) {
                        BodyMatcherType.JSON_PATH -> mappingBuilder.withRequestBody(matchingJsonPath(matcher.expr ?: "$"))

                        BodyMatcherType.EQUAL_TO_JSON -> {
                            val jsonNode = when (val v = matcher.value) {
                                is com.fasterxml.jackson.databind.JsonNode -> v
                                is String -> mapper.readTree(v)
                                null -> mapper.nullNode()
                                else -> mapper.valueToTree(v)
                            }
                            mappingBuilder.withRequestBody(
                                equalToJson(
                                    jsonNode.toString(),
                                    matcher.ignoreArrayOrder ?: true,
                                    matcher.ignoreExtraElements ?: true
                                )
                            )
                        }

                        BodyMatcherType.MATCHES -> mappingBuilder.withRequestBody(matching(matcher.value.toString()))
                        BodyMatcherType.CONTAINS -> mappingBuilder.withRequestBody(containing(matcher.value.toString()))
                    }
                }

                BodyMatchMode.TEXT -> bodyMatch.matchers.forEach { matcher ->
                    when (matcher.type) {
                        BodyMatcherType.MATCHES -> mappingBuilder.withRequestBody(matching(matcher.value.toString()))
                        BodyMatcherType.CONTAINS -> mappingBuilder.withRequestBody(containing(matcher.value.toString()))
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
                rb.proxiedFrom("{{${TemplateNames.SERVICE_ORIGIN} name=request.headers.[$X_MOCK_TARGET_SERVICE]}}")
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
            ReqMatchMethods.GET -> get(url)
            ReqMatchMethods.POST -> post(url)
            ReqMatchMethods.PUT -> put(url)
            ReqMatchMethods.PATCH -> patch(url)
            ReqMatchMethods.DELETE -> delete(url)
            ReqMatchMethods.HEAD -> head(url)
            ReqMatchMethods.OPTIONS -> options(url)
            ReqMatchMethods.ANY -> any(url)
        }
}

