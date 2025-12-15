package se.strawberry.config

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv

object Env {
    private val dotenv: Dotenv? = try {
        dotenv {
            directory = "."
            ignoreIfMalformed = true
            ignoreIfMissing = true
        }
    } catch (_: Exception) {
        null
    }

    private fun get(name: String): String? {
        // First check system environment, then .env file
        return System.getenv(name) ?: dotenv?.get(name)
    }

    // Type-safe methods using EnvVar
    fun string(envVar: EnvVar<String>): String {
        val value = get(envVar.key)?.takeIf { it.isNotBlank() }
        return value ?: envVar.default ?: throw MissingEnvironmentVariableException(envVar.key)
    }

    fun boolean(envVar: EnvVar<Boolean>): Boolean {
        val value = get(envVar.key)?.trim()?.lowercase()?.let {
            it == "1" || it == "true" || it == "yes" || it == "y"
        }
        return value ?: envVar.default ?: throw MissingEnvironmentVariableException(envVar.key)
    }

    fun int(envVar: EnvVar<Int>): Int {
        val value = get(envVar.key)?.trim()?.toIntOrNull()
        return value ?: envVar.default ?: throw MissingEnvironmentVariableException(envVar.key)
    }

    fun int(name: String, default: Int? = null): Int {
        val value = get(name)?.trim()?.toIntOrNull()
        return value ?: default ?: throw MissingEnvironmentVariableException(name)
    }
}

class MissingEnvironmentVariableException(variableName: String) :
    IllegalStateException("Required environment variable '$variableName' is not set and has no default value")

