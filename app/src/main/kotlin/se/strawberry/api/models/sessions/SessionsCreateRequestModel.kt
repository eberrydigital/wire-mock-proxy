package se.strawberry.api.models.sessions

import com.fasterxml.jackson.annotation.JsonInclude
import se.strawberry.api.models.DomainModel

/**
 * Request model for creating a new session.
 *
 * @property name Optional session name for identification
 * @property owner Optional owner identifier
 * @property expiresAt Optional unix timestamp when session should expire
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SessionsCreateRequestModel(
    val name: String? = null,
    val owner: String? = null,
    val expiresAt: Long? = null
): DomainModel()

