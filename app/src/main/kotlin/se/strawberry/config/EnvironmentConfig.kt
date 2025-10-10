package se.strawberry.config

object EnvironmentConfig {
    val proxyTarget: String = System.getenv("PROXY_TARGET") ?: "https://api.test.eberry.digital" // <-- OMNI base
    val port: Int = (System.getenv("PORT") ?: "8080").toInt()
    val bindAddress: String = System.getenv("BIND_ADDRESS") ?: "0.0.0.0" // data port bind
}