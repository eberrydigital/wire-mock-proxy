package se.strawberry.api

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import se.strawberry.api.handlers.RequestsHandler
import se.strawberry.api.handlers.StubsHandler
import se.strawberry.api.routing.ApiRoute
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.common.Paths
import se.strawberry.common.TransformerNames
import se.strawberry.maintenance.EphemeralCleaner

class RequestsApiTransformer : ResponseTransformerV2 {
    private val mapper = Json.mapper
    private val stubs = StubsHandler(mapper)
    private val requests = RequestsHandler(mapper)

    override fun getName(): String = TransformerNames.REQUESTS_API
    override fun applyGlobally(): Boolean = false

    override fun transform(response: Response, serveEvent: ServeEvent): Response {
        val req = serveEvent.request
        if (!req.url.startsWith(Paths.API_PREFIX)) return response

        EphemeralCleaner.pruneNow()

        return when (val route = ApiRoute.resolve(req)) {
            ApiRoute.NotApi -> response
            is ApiRoute.ListRequests   -> requests.list(route.query)
            is ApiRoute.GetRequestById -> requests.byId(route.id)
            ApiRoute.ClearRequests     -> requests.clear()
            ApiRoute.ExportRequests    -> requests.export()
            ApiRoute.CreateStub        -> stubs.create(req)
            ApiRoute.ListStubs         -> stubs.list()
            is ApiRoute.DeleteStub     -> stubs.delete(route.id)
            is ApiRoute.Unknown        -> notFound()
        }
    }

    private fun notFound(): Response =
        Response.response()
            .status(404)
            .headers(HttpHeaders(HttpHeader.httpHeader(Headers.CONTENT_TYPE, Headers.JSON)))
            .body("""{"error":"not_found"}""")
            .build()

    // --------- Router ---------
}
