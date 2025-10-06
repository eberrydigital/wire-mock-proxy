package se.strawberry.config

data class Settings(
    val proxyTarget: String,
    val port: Int,
    val adminPort: Int,
    val bindAddress: String,
    val adminBindAddress: String,
    val adminApiToken: String?
) {
    companion object {
        fun fromEnv(): Settings = Settings(
            proxyTarget = EnvironmentConfig.proxyTarget,
            port = EnvironmentConfig.port,
            adminPort = EnvironmentConfig.adminPort,
            bindAddress = EnvironmentConfig.bindAddress,
            adminBindAddress = EnvironmentConfig.adminBindAddress,
            adminApiToken = EnvironmentConfig.adminApiToken
        )
    }
}
