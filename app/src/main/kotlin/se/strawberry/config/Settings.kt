package se.strawberry.config

data class Settings(
    val proxyTarget: String,
    val port: Int,
    val bindAddress: String,
) {
    companion object {
        fun fromEnv(): Settings = Settings(
            proxyTarget = EnvironmentConfig.proxyTarget,
            port = EnvironmentConfig.port,
            bindAddress = EnvironmentConfig.bindAddress
        )
    }
}
