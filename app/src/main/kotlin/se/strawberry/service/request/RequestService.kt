package se.strawberry.service.request

import se.strawberry.api.models.traffic.RecordedTrafficInstanceModel

interface RequestService {
    /** List recorded requests with optional filters. */
    fun list(query: Map<String, String>): List<RecordedTrafficInstanceModel>

    /** Get recorded request by id. */
    fun byId(id: String): RecordedTrafficInstanceModel?

    /** Clear recorded requests. */
    fun clear()

    /** Export recorded requests as NDJSON. */
    fun exportAsNdjson(): String
}

