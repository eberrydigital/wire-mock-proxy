package se.strawberry.config

import java.net.URI


data class AppConfig(
    val wireMockServerPort: Int,
    val ktorApiPort: Int,
    val hostAddress: String,
    val allowedPorts: Set<Int>,
    val services: Map<String, URI>,
    val dynamo: DynamoConfig = DynamoConfig()
)
