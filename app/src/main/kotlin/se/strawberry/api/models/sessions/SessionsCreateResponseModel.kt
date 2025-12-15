package se.strawberry.api.models.sessions

import com.fasterxml.jackson.annotation.JsonInclude
import se.strawberry.api.models.DomainModel
import se.strawberry.repository.session.SessionRepository

/**
 * Response model for session information.
 *
 * @property id Unique session identifier
 * @property status Session status (ACTIVE, CLOSED, EXPIRED)
 * @property name Optional session name
 * @property owner Optional session owner identifier
 * @property createdAt Unix timestamp when session was created
 * @property expiresAt Optional unix timestamp when session expires
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SessionsCreateResponseModel(
    val id: String,
    val status: String,
    val name: String? = null,
    val owner: String? = null,
    val createdAt: Long,
    val expiresAt: Long? = null
): DomainModel()

/**
 * Convert SessionRepository.Session to SessionResponse API model
 */
fun SessionRepository.Session.toResponse(): SessionsCreateResponseModel = SessionsCreateResponseModel(
    id = id,
    status = status.name,
    name = name,
    owner = owner,
    createdAt = createdAt,
    expiresAt = expiresAt
)

