package se.strawberry.onboarding_lessons

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.client.WireMock.*

// -- First Steps --

// Initialize Server for WireMock
// Create the stub
// Use any client i.e. Postman to execute http://localhost:8080/lesson/1

fun main() {
    val wireMockConfig = options()
        .bindAddress("0.0.0.0")
        .port(8080)
    val server = WireMockServer(wireMockConfig)
    server.start()

    server.givenThat(
        get(urlPathEqualTo("/lesson/1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\": \"Hi QA Team\"}")
            )
    )
}
