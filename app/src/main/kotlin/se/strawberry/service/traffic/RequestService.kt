package se.strawberry.service.traffic

import com.github.tomakehurst.wiremock.http.Response

/**
 * Service boundary for recorded traffic (requests/responses).
 * R1.2: interface only; DB comes in R4.
 */
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

