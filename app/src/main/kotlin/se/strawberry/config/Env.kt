package se.strawberry.config

object Env {
    fun str(name: String, default: String? = null): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

    fun bool(name: String, default: Boolean = false): Boolean =
        System.getenv(name)?.trim()?.lowercase()?.let {
            it == "1" || it == "true" || it == "yes" || it == "y"
        } ?: default

    fun int(name: String, default: Int? = null): Int? =
        System.getenv(name)?.trim()?.toIntOrNull() ?: default
}