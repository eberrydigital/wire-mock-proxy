package se.strawberry.stubs

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.common.Metadata
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.matching.UrlPattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping

data class Ephemeral(
    val uses: Int? = 1,
    val ttlMs: Long? = null, //time to live in ms
)

data class Patch(
    val merge: Any? = null,
    val jsonPatch: List<Map<String, Any>>? = null,
)

enum class RespMode { STATIC, PATCH_UPSTREAM }

data class RespDef(
    val mode: RespMode,
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val bodyJson: Any? = null,
    val bodyText: String? = null,
    val patch: Patch? = null,
)

data class BodyMatcher(
    val type: BodyMatcherType,
    val expr: String? = null,
    val value: Any? = null,
    val ignoreArrayOrder: Boolean? = null,
    val ignoreExtraElements: Boolean? = null,
)

enum class BodyMatcherType {
    EQUAL_TO_JSON,
    JSON_PATH,
    MATCHES,
    CONTAINS
}

data class BodyMatch(
    val mode: BodyMatchMode,
    val matchers: List<BodyMatcher> = emptyList(),
)

enum class BodyMatchMode {
    JSON,
    TEXT
}


data class UrlMatch(
    val type: UrlMatchType,
    val value: String,
)

enum class UrlMatchType {
    EXACT,
    LOOSENED,
}

data class HeaderMatch(
    val type: HeaderMatchType,
    val value: String,
)

enum class HeaderMatchType {
    EQUAL_TO,
    MATCHES,
    CONTAINS
}

data class ReqMatch(
    val method: ReqMatchMethods,
    val url: UrlMatch,
    val headers: Map<String, HeaderMatch> = emptyMap(),
    val body: BodyMatch? = null,
)

enum class ReqMatchMethods {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, ANY
}

data class CreateStubRequest(
    val request: ReqMatch,
    val response: RespDef,
    val priority: Int? = 2,
    val ephemeral: Ephemeral? = Ephemeral(uses = 1, ttlMs = null
    ),
)

private val mapper = jacksonObjectMapper()

fun buildStubMapping(dto: CreateStubRequest, proxiedTarget: String?): StubMapping {
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
            rb.proxiedFrom(requireNotNull(proxiedTarget) { "proxyTarget is required for patchUpstream" })
            rb.withTransformers("upstream-patch", "one-shot-postserve")
            rb.withTransformerParameter("patch", mapper.valueToTree(dto.response.patch))
        }
    }

    val needsOneShot = (dto.ephemeral?.uses != null) || (dto.ephemeral?.ttlMs != null)
    if (needsOneShot) {
        mappingBuilder.withServeEventListener("one-shot", Parameters.empty())
    }


    var builder = mappingBuilder.atPriority(dto.priority ?: 2)
        .willReturn(rb)

    val expiresAtMs: Long? = dto.ephemeral?.ttlMs?.let { System.currentTimeMillis() + it }
    expiresAtMs?.let {  builder = builder.andMatching("ttl-guard", Parameters.one("expiresAtMs", it)) }
    if (dto.ephemeral?.uses != null || expiresAtMs != null) {
        builder = builder.withServeEventListener("one-shot", Parameters.empty())
    }


    val stub = builder.build()


    val md = Metadata.metadata()
        .apply {
            dto.ephemeral?.uses?.let { attr("remainingUses", it) }
            dto.ephemeral?.ttlMs?.let { ttl -> attr("expiresAt", System.currentTimeMillis() + ttl) }
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
