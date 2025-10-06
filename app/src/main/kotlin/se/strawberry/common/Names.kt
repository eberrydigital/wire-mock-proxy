package se.strawberry.common

object ListenerNames {
    const val EPHEMERAL_LISTENER = "ephemeral-listener"
}

object MatcherNames {
    const val TTL_GUARD = "ttl-guard"
}

object TransformerNames {
    const val REQUESTS_API = "requests-api"
    const val UPSTREAM_PATCH = "upstream-patch"
}

object Paths {
    const val ADMIN_PREFIX = "/__admin"
    const val UI_ROOT = "/_proxy-ui"
    const val UI_ASSETS_PREFIX = "/_proxy-ui/assets"
    const val API_PREFIX = "/_proxy-api"
    const val API_STUBS = "$API_PREFIX/stubs"
}

object Headers {
    const val CONTENT_TYPE = "Content-Type"
    const val CACHE_CONTROL = "Cache-Control"
    const val AUTHORIZATION = "Authorization"
    const val X_TEST_RUN_ID = "X-Test-Run-Id"

    const val JSON = "application/json"
    const val JSON_UTF8 = "application/json; charset=utf-8"
    const val HTML_UTF8 = "text/html; charset=utf-8"
    const val CSS_UTF8 = "text/css; charset=utf-8"
    const val JS_UTF8 = "application/javascript; charset=utf-8"
    const val X_FORWARDED_HOST = "X-Forwarded-Host"
    const val X_FORWARDED_PROTO = "X-Forwarded-Proto"
}

object Priorities {
    const val UI = 1
    const val DEFAULT_STUB = 2
    const val PROXY_FALLBACK = 1000
}

object MetadataKeys {
    const val REMAINING_USES = "remainingUses"
    const val EXPIRES_AT = "expiresAt"
}
