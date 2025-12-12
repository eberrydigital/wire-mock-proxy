package se.strawberry.service.stub

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.Response
import se.strawberry.api.handlers.SessionScope
import se.strawberry.api.handlers.StubsHandler
import se.strawberry.domain.stub.CreateStubRequest
import se.strawberry.stubs.dto.StubBuilder

/**
 * R1.4: Thin adapter over existing StubsHandler to fit the service boundary.
 * No behavior changes.
 */
class StubServiceImpl(
    private val mapper: ObjectMapper
) : StubService {
    private val handler = StubsHandler(mapper)

    override fun create(dto: CreateStubRequest, originalRequest: Request, sessionId: String?): Response {
        // Apply session scoping as handler would
        val patched = SessionScope.withSessionMatch(dto, sessionId)
        val stub = StubBuilder.buildStubMapping(patched)
        // Delegate actual add + response payload building to handler
        return handler.create(originalRequest)
    }

    override fun list(): Response = handler.list()

    override fun delete(id: String): Response = handler.delete(id)
}

