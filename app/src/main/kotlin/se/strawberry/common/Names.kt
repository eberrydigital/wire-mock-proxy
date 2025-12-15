package se.strawberry.common

object ListenerNames {
    const val EPHEMERAL_LISTENER = "ephemeral-listener"
}

object MatcherNames {
    const val TTL_GUARD = "ttl-guard"
}

object FilterNames {
    const val DYNAMIC_ROUTING_GUARD = "dynamic-routing-guard"
}

object TemplateNames {
    const val SERVICE_ORIGIN = "service-origin"
    const val SERVICE_TEMPLATE_HELPERS = "service-template-helpers"
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
    const val JSON = "application/json"
    const val X_MOCK_TARGET_SERVICE = "X-Mock-Target-Service"
    const val X_MOCK_SESSION_ID = "X-Mock-Session-Id"
}

object Priorities {
    const val UI = 1
    const val PROXY_FALLBACK = 1000
}

object MetadataKeys {
    const val REMAINING_USES = "remainingUses"
    const val EXPIRES_AT = "expiresAt"
}
