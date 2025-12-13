package se.strawberry.service.stub

import com.github.tomakehurst.wiremock.http.Response
import se.strawberry.domain.stub.CreateStubRequest

/**
 * Service boundary for stub lifecycle operations.
 * R1.2: interface only; existing handlers still call WireMock directly.
 */
interface StubService {
    /** Create a stub from request DTO; sessionId is optional for global vs session-scoped stubs. */
    fun create(dto: CreateStubRequest, sessionId: String): Response

    /** List all stubs (thin wrapper around WireMock list). */
    fun list(): Response

    /** Delete stub by id (delegates to underlying WireMock client). */
    fun delete(id: String): Response
}

