package se.strawberry.config

import org.slf4j.LoggerFactory
import java.net.URI


object AppConfigLoader {
    private val log = LoggerFactory.getLogger(AppConfigLoader::class.java)

    fun load(): AppConfig {
        val port = Env.int(EnvVar.WireMockServerPort)
        require(port in 1..65535) { "PORT must be in 1..65535" }

        val ktorApiPort = Env.int(EnvVar.KtorApiPort)
        require(ktorApiPort in 1..65535 && ktorApiPort != port) { "API_PORT must be in 1..65535 and different from PORT" }

        val hostAddress = Env.string(EnvVar.HostAddress)

        val allowedPorts = parseAllowedPorts(Env.string(EnvVar.DynAllowedPorts))
        require(allowedPorts.isNotEmpty()) { "DYN_ALLOWED_PORTS resulted in empty set" }

        val services = parseServiceMap(Env.string(EnvVar.ServiceMap))
        require(services.isNotEmpty()) {
            "SERVICE_MAP is empty. Provide at least one 'name=url' pair, e.g. SERVICE_MAP=omni=http://127.0.0.1:5000"
        }

        // DynamoDB config
        val dynamoEndpoint = URI(Env.string(EnvVar.DynamoEndpoint))
        val dynamoRegion = Env.string(EnvVar.AwsRegion)
        val accessKeyId = Env.string(EnvVar.AwsAccessKeyId)
        val secretAccessKey = Env.string(EnvVar.AwsSecretAccessKey)

        val cfg = AppConfig(
            wireMockServerPort = port,
            ktorApiPort = ktorApiPort,
            hostAddress = hostAddress,
            allowedPorts = allowedPorts,
            services = services,
            dynamo = DynamoConfig(
                endpoint = dynamoEndpoint,
                region = dynamoRegion,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
            )
        )

        log.info(
            "AppConfig => port={}, bind={}, allowedPorts={}, services={}, dynamoEndpoint={}, dynamoRegion={}",
            cfg.wireMockServerPort, cfg.hostAddress,
            cfg.allowedPorts.sorted().joinToString(","),
            cfg.services.keys.sorted().joinToString(","),
            cfg.dynamo.endpoint?.toString() ?: "(default)",
            cfg.dynamo.region
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
            val url = p.substring(eq + 1).trim().removeSuffix("/")
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
