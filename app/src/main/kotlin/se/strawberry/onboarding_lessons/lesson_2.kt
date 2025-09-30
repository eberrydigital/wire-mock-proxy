import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders

// -- Multiple Headers --

// Initialize Server for WireMock
// Create the stub
// Use any client i.e. Postman to execute http://localhost:8080/lesson/2
// Observe the response headers

fun main() {
    val wireMockConfig = options()
        .bindAddress("0.0.0.0")
        .port(8080)
    val server = WireMockServer(wireMockConfig)
    server.start()

    val headers = HttpHeaders(listOf(
        HttpHeader("Content-Type", "application/json"),
        HttpHeader("First-Custom-Header", "Wire"),
        HttpHeader("First-Custom-Header", "Mock")
    ))

    server.givenThat(
        get(urlPathEqualTo("/lesson/2"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeaders(headers)
                    .withBody("{\"message\": \"Welcome to lesson_2\"}")
            )
    )
}