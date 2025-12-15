package se.strawberry.api.models.traffic

import com.fasterxml.jackson.annotation.JsonInclude
import se.strawberry.api.models.DomainModel

/**
 *  A model for HTTP request information in recorded traffic.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HttRequestModel(
    val method: String,
    val url: String,
    val headers: Map<String, String?>,
    val body: String? = null,
    val contentType: String? = null
): DomainModel()

