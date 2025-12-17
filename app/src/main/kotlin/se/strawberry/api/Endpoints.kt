package se.strawberry.api

object Endpoints {
    const val BASE = "/_proxy-api"
    object Paths {
        const val HEALTH = "$BASE/health"
        const val STUBS = "$BASE/stubs"
        const val TRAFFIC = "$BASE/traffic"
        const val SESSIONS = "$BASE/sessions"
        const val WS_TRAFFIC = "$BASE/ws/traffic"
    }
}