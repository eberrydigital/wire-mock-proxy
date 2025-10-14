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
import se.strawberry.maintenance.EphemeralCleaner
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
    private sealed class ApiRoute {
        sealed class Match : ApiRoute()
        data object NotApi : ApiRoute()
        data class Unknown(val path: String) : Match()

        // /_proxy-api/requests(?query)
        data class ListRequests(val query: Map<String, String>) : Match()
        // /_proxy-api/requests/{id}
        data class GetRequestById(val id: String) : Match()
        // POST /_proxy-api/requests/clear
        data object ClearRequests : Match()
        // GET /_proxy-api/export
        data object ExportRequests : Match()

        // /_proxy-api/stubs (GET/POST)
        data object CreateStub : Match()
        data object ListStubs : Match()
        // DELETE /_proxy-api/stubs/{id}
        data class DeleteStub(val id: String) : Match()

        companion object {
            fun resolve(req: com.github.tomakehurst.wiremock.http.Request): ApiRoute {
                val url = req.url
                if (!url.startsWith(Paths.API_PREFIX)) return NotApi

                val method = req.method.value()
                val (route, queryMap) = splitPathAndQueryToMap(url.removePrefix(Paths.API_PREFIX))

                return when {
                    // ----- REQUESTS -----
                    route == "/requests" && method == "GET" -> ListRequests(queryMap)

                    route.startsWith("/requests/") && method == "GET" -> {
                        val id = route.removePrefix("/requests/").trim('/')
                        if (id.isEmpty()) Unknown(route) else GetRequestById(id)
                    }

                    route == "/requests/clear" && method == "POST" -> ClearRequests
                    route == "/export" && method == "GET" -> ExportRequests

                    // ----- STUBS -----
                    route == "/stubs" && method == "POST" -> CreateStub
                    route == "/stubs" && method == "GET"  -> ListStubs

                    route.startsWith("/stubs/") && method == "DELETE" -> {
                        val id = route.removePrefix("/stubs/").trim('/')
                        if (id.isEmpty()) Unknown(route) else DeleteStub(id)
                    }

                    else -> Unknown(route)
                }
            }

            private fun splitPathAndQueryToMap(raw: String): Pair<String, Map<String, String>> {
                val qIdx = raw.indexOf('?')
                if (qIdx < 0) return raw to emptyMap()

                val path = raw.substring(0, qIdx)
                val queryRaw = raw.substring(qIdx + 1)

                val map = mutableMapOf<String, String>()
                if (queryRaw.isNotEmpty()) {
                    queryRaw.split('&').forEach { pair ->
                        if (pair.isBlank()) return@forEach
                        val eq = pair.indexOf('=')
                        val key = if (eq >= 0) pair.substring(0, eq) else pair
                        val value = if (eq >= 0) pair.substring(eq + 1) else ""
                        val k = urlDecode(key)
                        if (!map.containsKey(k)) {
                            map[k] = urlDecode(value)
                        }
                    }
                }
                return path to map
            }

            private fun urlDecode(s: String): String =
                URLDecoder.decode(s, StandardCharsets.UTF_8)
        }
    }
}
