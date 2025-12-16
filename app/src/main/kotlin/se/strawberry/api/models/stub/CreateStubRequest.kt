package se.strawberry.api.models.stub

data class CreateStubRequest(
    val request: ReqMatch,
    val response: RespDef,
    val priority: Int? = 2,
    val ephemeral: Ephemeral? = Ephemeral(uses = 1, ttlMs = null),
)

data class ReqMatch(
    val method: ReqMatchMethods,
    val url: UrlMatch,
    val headers: Map<String, HeaderMatch> = emptyMap(),
    val body: BodyMatch? = null,
)

enum class ReqMatchMethods {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, ANY
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
