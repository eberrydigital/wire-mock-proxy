package se.strawberry.config

object EnvironmentConfig {
    // We read configuration primarily from environment variables.
    // CLI flags are also supported as a simple override for local runs/CI scripts.
    // Defaults are conservative and safe for local dev.
    val proxyTarget: String = System.getenv("PROXY_TARGET") ?: "https://api.test.eberry.digital" // <-- OMNI base
    val port: Int = (System.getenv("PORT") ?: "8080").toInt()
    val adminPort: Int = (System.getenv("ADMIN_PORT") ?: "8081").toInt()
    val bindAddress: String = System.getenv("BIND_ADDRESS") ?: "0.0.0.0" // data port bind
    val adminBindAddress: String = System.getenv("ADMIN_BIND_ADDRESS") ?: "127.0.0.1" // admin bound to localhost by default
    val defaultExpireMs: Long = (System.getenv("DEFAULT_EXPIRE_MS") ?: "15000").toLong() // helper default timeout for one-offs
    val adminApiToken: String? = System.getenv("ADMIN_API_TOKEN") // optional token guard; see README for safe enablement
}