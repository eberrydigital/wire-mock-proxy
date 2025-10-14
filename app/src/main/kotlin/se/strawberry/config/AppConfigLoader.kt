package se.strawberry.config

import org.slf4j.LoggerFactory
import java.net.URI


object AppConfigLoader {
    private val log = LoggerFactory.getLogger(AppConfigLoader::class.java)

    fun load(): AppConfig {
        val port = Env.int("PORT", 8080)!!
        require(port in 1..65535) { "PORT must be in 1..65535" }

        val bindAddress = Env.str("BIND_ADDRESS", "0.0.0.0")!!

        val filesSource = Env.str("WIREMOCK_FILES_DIR")?.let {
            AppConfig.FilesSource.Directory(it)
        } ?: AppConfig.FilesSource.Classpath("wiremock")

        val allowedPorts = parseAllowedPorts(Env.str("DYN_ALLOWED_PORTS", "80,443")!!)
        require(allowedPorts.isNotEmpty()) { "DYN_ALLOWED_PORTS resulted in empty set" }

        val services = parseServiceMap(Env.str("SERVICE_MAP") ?: "")
        require(services.isNotEmpty()) {
            "SERVICE_MAP is empty. Provide at least one 'name=url' pair, e.g. SERVICE_MAP=omni=http://127.0.0.1:5000"
        }

        val uiEnabled = Env.bool("ENABLE_PROXY_UI", true)

        val cfg = AppConfig(
            port = port,
            bindAddress = bindAddress,
            filesSource = filesSource,
            allowedPorts = allowedPorts,
            services = services,
            uiEnabled = uiEnabled
        )

        val filesSrcLog = when (filesSource) {
            is AppConfig.FilesSource.Classpath -> "classpath:${filesSource.root}"
            is AppConfig.FilesSource.Directory -> "dir:${filesSource.path}"
        }
        log.info(
            "AppConfig => port={}, bind={}, files={}, allowedPorts={}, services={}",
            cfg.port, cfg.bindAddress, filesSrcLog,
            cfg.allowedPorts.sorted().joinToString(","),
            cfg.services.keys.sorted().joinToString(",")
        )

        return cfg
    }

    private fun parseAllowedPorts(raw: String): Set<Int> =
        raw.split(',', ';')
            .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toIntOrNull() }
            .filter { it in 1..65535 }
            .toSet()

    private fun parseServiceMap(raw: String): Map<String, URI> {
        if (raw.isBlank()) return emptyMap()
        val res = linkedMapOf<String, URI>()
        raw.split(',').forEachIndexed { idx, pair ->
            val p = pair.trim()
            if (p.isBlank()) return@forEachIndexed
            val eq = p.indexOf('=')
            require(eq > 0 && eq < p.lastIndex) {
                "SERVICE_MAP: entry #$idx '$p' must be 'name=url'"
            }
            val key = p.substring(0, eq).trim()
            val url = p.substring(eq + 1).trim()
            require(key.isNotEmpty()) { "SERVICE_MAP: empty service name in '$p'" }
            val uri = try { URI(url) } catch (e: Exception) {
                throw IllegalArgumentException("SERVICE_MAP: invalid URL '$url' for key '$key'", e)
            }
            require(uri.scheme == "http" || uri.scheme == "https") {
                "SERVICE_MAP: unsupported scheme '${uri.scheme}' for '$key' (http/https only)"
            }
            if (res.putIfAbsent(key, uri) != null) {
                throw IllegalArgumentException("SERVICE_MAP: duplicate key '$key'")
            }
        }
        return res
    }
}
