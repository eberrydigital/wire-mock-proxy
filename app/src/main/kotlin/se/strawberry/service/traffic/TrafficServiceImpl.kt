package se.strawberry.service.traffic

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.Response
import se.strawberry.api.handlers.RequestsHandler

/**
 * R1.4: Thin adapter over existing RequestsHandler to fit the service boundary.
 * No behavior changes.
 */
class TrafficServiceImpl(
    private val mapper: ObjectMapper
) : TrafficService {
    private val handler = RequestsHandler(mapper)

    override fun list(query: Map<String, String>): Response = handler.list(query)

    override fun byId(id: String): Response = handler.byId(id)

    override fun clear(): Response = handler.clear()

    override fun export(): Response = handler.export()
}

