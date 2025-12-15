package se.strawberry.api.models.health

import com.fasterxml.jackson.annotation.JsonInclude
import se.strawberry.api.models.DomainModel

/**
 * Response model for health check endpoint.
 *
 * @property status Health status of the service (e.g., "ok", "degraded")
 * @property timestamp Unix timestamp when the health check was performed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HealthResponse(
    val status: String = "ok",
    val timestamp: Long = System.currentTimeMillis(),
): DomainModel()

