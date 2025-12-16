package stubs

import se.strawberry.api.models.stub.CreateStubRequest
import se.strawberry.api.models.stub.Ephemeral
import se.strawberry.api.models.stub.ReqMatch
import se.strawberry.api.models.stub.ReqMatchMethods
import se.strawberry.api.models.stub.RespDef
import se.strawberry.api.models.stub.RespMode
import se.strawberry.api.models.stub.UrlMatch
import se.strawberry.api.models.stub.UrlMatchType

object Stubs {

    fun createStubRequest(
        url: String,
        status: Int = 200,
        bodyText: String = "ok",
        ephemeral: Ephemeral? = null,
    ): CreateStubRequest {
        val urlDef = UrlMatch(
            type = UrlMatchType.EXACT,
            value = url
        )

        val reqDef = ReqMatch(
            method = ReqMatchMethods.GET,
            url = urlDef,
            headers = emptyMap(),
            body = null
        )

        val respDef = RespDef(
            mode = RespMode.STATIC,
            status = status,
            headers = mapOf("Content-Type" to "text/plain; charset=utf-8"),
            bodyText = bodyText,
            bodyJson = null,
            patch = null
        )

        return CreateStubRequest(
            request = reqDef,
            response = respDef,
            ephemeral = ephemeral
        )
    }
}
