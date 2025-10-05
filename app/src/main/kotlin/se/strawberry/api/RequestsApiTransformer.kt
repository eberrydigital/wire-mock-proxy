package se.strawberry.api

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import se.strawberry.api.handlers.RequestsHandler
import se.strawberry.api.handlers.StubsHandler
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.common.Paths
import se.strawberry.common.TransformerNames
import se.strawberry.config.EnvironmentConfig

class RequestsApiTransformer : ResponseTransformerV2 {

    private val mapper = Json.mapper
    private val stubs = StubsHandler(mapper)
    private val requests = RequestsHandler(mapper)

    override fun getName(): String = TransformerNames.REQUESTS_API
    override fun applyGlobally(): Boolean = false

    override fun transform(response: Response, serveEvent: ServeEvent): Response {
        val req = serveEvent.request

        EnvironmentConfig.adminApiToken?.let { token ->
            val header = req.headers?.getHeader("Authorization")?.takeIf { it.isPresent }?.firstValue()
            if (header == null || header != "Bearer $token") {
                return Response.response()
                    .status(401)
                    .headers(jsonHeaders())
                    .body("""{"error":"unauthorized"}""")
                    .build()
            }
        }

        val url = req.url
        if (!url.startsWith(Paths.API_PREFIX)) return response

        val (route, query) = requests.splitPathAndQuery(url.removePrefix(Paths.API_PREFIX))

        return when {
            route == "/requests" -> requests.list(query)

            route.startsWith("/requests/") -> {
                val id = route.removePrefix("/requests/").trim('/')
                requests.byId(id)
            }

            route == "/requests/clear" && req.method.value() == "POST" -> requests.clear()
            route == "/export" -> requests.export()
            route == "/stubs" && req.method.value() == "POST" -> stubs.create(req)
            route == "/stubs" && req.method.value() == "GET"  -> stubs.list()

            // /stubs/{id} (DELETE)
            route.startsWith("/stubs/") && req.method.value() == "DELETE" -> {
                val id = route.removePrefix("/stubs/").trim('/')
                stubs.delete(id)
            }

            else -> Response.response()
                .status(404)
                .headers(jsonHeaders())
                .body("""{"error":"not_found"}""")
                .build()
        }
    }

    private fun jsonHeaders() =
        HttpHeaders(HttpHeader.httpHeader(Headers.CONTENT_TYPE, Headers.JSON))
}
