package se.strawberry.helpers

import se.strawberry.api.models.stub.CreateStubRequest
import se.strawberry.api.models.stub.HeaderMatch
import se.strawberry.api.models.stub.HeaderMatchType
import se.strawberry.common.Headers

object SessionHelper {

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