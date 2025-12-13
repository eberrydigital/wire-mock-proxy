package se.strawberry.service.request

import com.github.tomakehurst.wiremock.http.Response

interface RequestService {
    /** List recorded requests with optional filters. */
    fun list(query: Map<String, String>): Response

    /** Get recorded request by id. */
    fun byId(id: String): Response

    /** Clear recorded requests. */
    fun clear(): Response

    /** Export recorded requests as NDJSON. */
    fun export(): Response
}

