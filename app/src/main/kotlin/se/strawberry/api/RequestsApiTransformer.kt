package se.strawberry.api

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import se.strawberry.api.routing.ApiRoute
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.common.Paths
import se.strawberry.common.TransformerNames
import se.strawberry.domain.stub.CreateStubRequest
import se.strawberry.helpers.SessionHelper
import se.strawberry.maintenance.EphemeralCleaner
import se.strawberry.service.stub.StubServiceImpl
import se.strawberry.service.request.RequestServiceImpl
import se.strawberry.service.wiremock.ServerWireMockClient

class RequestsApiTransformer : ResponseTransformerV2 {
    private val wireMockClient = ServerWireMockClient()
    private val mapper = Json.mapper
    private val stubService = StubServiceImpl(mapper, wireMockClient)
    private val requestService = RequestServiceImpl(mapper, wireMockClient)

    override fun getName(): String = TransformerNames.REQUESTS_API
    override fun applyGlobally(): Boolean = false

    override fun transform(response: Response, serveEvent: ServeEvent): Response {
        val req = serveEvent.request
        if (!req.url.startsWith(Paths.API_PREFIX)) return response

        EphemeralCleaner.pruneNow()

        return when (val route = ApiRoute.resolve(req)) {
            ApiRoute.NotApi -> response
            is ApiRoute.ListRequests   -> requestService.list(route.query)
            is ApiRoute.GetRequestById -> requestService.byId(route.id)
            ApiRoute.ClearRequests     -> requestService.clear()
            ApiRoute.ExportRequests    -> requestService.export()
            ApiRoute.CreateStub -> {
                val body = req.bodyAsString ?: ""
                if (body.isBlank()) return badRequest("empty_body")

                val dto = try {
                    mapper.readValue(body, CreateStubRequest::class.java)
                } catch (e: Exception) {
                    return badRequest("invalid_json")
                }

                val sessionId = SessionHelper.extractSessionId(req)
                    ?: return badRequest("missing_session")

                stubService.create(dto, sessionId)
            }
            ApiRoute.ListStubs         -> stubService.list()
            is ApiRoute.DeleteStub     -> stubService.delete(route.id)
            is ApiRoute.Unknown        -> notFound()
        }
    }

    private fun notFound(): Response =
        Response.response()
            .status(404)
            .headers(HttpHeaders(HttpHeader.httpHeader(Headers.CONTENT_TYPE, Headers.JSON)))
            .body("""{"error":"not_found"}""")
            .build()

    private fun badRequest(reason: String): Response =
        Response.response()
            .status(400)
            .headers(HttpHeaders(HttpHeader.httpHeader(Headers.CONTENT_TYPE, Headers.JSON)))
            .body("""{"error":"bad_request","reason":"$reason"}""")
            .build()
}
