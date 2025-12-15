package se.strawberry.api.models.traffic

import com.fasterxml.jackson.annotation.JsonInclude
import se.strawberry.api.models.DomainModel

/**
 * Response model for HTTP response information in recorded traffic.
 *
 * @property status HTTP status code
 * @property headers Response headers (sensitive headers are masked)
 * @property body Optional response body (only included in detail view)
 * @property contentType Content-Type header value
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HttpResponseModel(
    val status: Int,
    val headers: Map<String, String?>,
    val body: String? = null,
    val contentType: String? = null
): DomainModel()

