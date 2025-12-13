package se.strawberry.helpers

import com.github.tomakehurst.wiremock.http.Request
import se.strawberry.common.Headers
import se.strawberry.domain.stub.CreateStubRequest
import se.strawberry.domain.stub.HeaderMatch
import se.strawberry.domain.stub.HeaderMatchType

object SessionHelper {

    fun extractSessionId(req: Request): String? =
        req.getHeader(Headers.X_MOCK_SESSION_ID)?.trim()?.takeIf { it.isNotEmpty() }

     fun withSessionMatch(dto: CreateStubRequest, sessionId: String?): CreateStubRequest {
        if (sessionId.isNullOrBlank()) return dto
        val headers = dto.request.headers + (
                Headers.X_MOCK_SESSION_ID to HeaderMatch(
                    type = HeaderMatchType.EQUAL_TO,
                    value = sessionId
                )
                )
        return dto.copy(request = dto.request.copy(headers = headers))
    }
}