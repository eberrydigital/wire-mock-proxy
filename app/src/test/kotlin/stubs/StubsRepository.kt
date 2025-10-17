package stubs

import se.strawberry.stubs.dto.*

object Stubs {

    fun getExactStaticText(
        url: String,
        status: Int = 200,
        bodyText: String = "ok",
        uses: Int? = null,
        ttlMs: Long? = null
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

        val ephemeral = if (uses != null || ttlMs != null) Ephemeral(uses = uses, ttlMs = ttlMs) else null

        return CreateStubRequest(
            request = reqDef,
            response = respDef,
            ephemeral = ephemeral
        )
    }
}
