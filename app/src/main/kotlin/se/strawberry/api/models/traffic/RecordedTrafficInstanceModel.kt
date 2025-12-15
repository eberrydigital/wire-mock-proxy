package se.strawberry.api.models.traffic

import com.fasterxml.jackson.annotation.JsonInclude
import se.strawberry.api.models.DomainModel

/**
 * Response model for recorded traffic event.
 *
 * @property id Unique identifier for this traffic event
 * @property receivedAt Unix timestamp when request was received
 * @property timingMs Total time in milliseconds to process the request
 * @property request HTTP request details
 * @property response HTTP response details
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecordedTrafficInstanceModel(
    val id: String,
    val receivedAt: Long,
    val timingMs: Int? = null,
    val request: HttRequestModel,
    val response: HttpResponseModel
): DomainModel()

