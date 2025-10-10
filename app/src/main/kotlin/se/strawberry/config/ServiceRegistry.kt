package se.strawberry.config

/**
 * Simple service registry: maps "service name" -> absolute origin ("https://api.example.com[:port]").
 * Populated from environment variable SERVICE_MAP.
 */
class ServiceRegistry(
    private val map: Map<String, String>
) {
    fun resolve(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return map[name.trim().lowercase()]
    }

    companion object {
        fun fromEnv(): ServiceRegistry {
            val env = System.getenv("SERVICE_MAP")?.trim().orEmpty()
            // format: "key=url,key=url" i.e. "omni_personalization=https://api.test.eberry.digital"
            val pairs = env.split(',').mapNotNull { entry ->
                val s = entry.trim()
                if (s.isEmpty()) return@mapNotNull null
                val i = s.indexOf('=')
                if (i <= 0 || i == s.lastIndex) return@mapNotNull null
                val key = s.substring(0, i).trim().lowercase()
                val valUrl = s.substring(i + 1).trim()
                if (key.isEmpty() || valUrl.isEmpty()) null else key to valUrl
            }
            return ServiceRegistry(pairs.toMap())
        }
    }

    override fun toString(): String = map.toString()
}
