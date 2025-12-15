package se.strawberry.config

import java.net.URI


data class AppConfig(
    val wireMockServerPort: Int,
    val ktorApiPort: Int,
    val hostAddress: String,
    val allowedPorts: Set<Int>,
    val services: Map<String, URI>,
    val dynamo: DynamoConfig = DynamoConfig()
) {
    sealed class FilesSource {
        data class Classpath(val root: String = "wiremock") : FilesSource()
        data class Directory(val path: String) : FilesSource()
    }
}
