package se.strawberry.config

import java.net.URI


data class AppConfig(
    val port: Int,
    val bindAddress: String,
    val filesSource: FilesSource,
    val allowedPorts: Set<Int>,
    val services: Map<String, URI>,
    val uiEnabled: Boolean = true
) {
    sealed class FilesSource {
        data class Classpath(val root: String = "wiremock") : FilesSource()
        data class Directory(val path: String) : FilesSource()
    }
}
