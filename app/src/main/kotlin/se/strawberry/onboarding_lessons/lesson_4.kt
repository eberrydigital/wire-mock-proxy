package se.strawberry.onboarding_lessons

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders

// -- Priorities --


// In the previous lesson we encountered a situation where a more generic stub was matching requests and all the previous stabs were ignored.
// We'll use priorities to solve this issue. Note the lower the number, the higher the priority.
// We'll also use the wildcard regular expression so that anything that follows /lesson/ will match the stub.

fun main() {
    val wireMockConfig = options()
        .bindAddress(EnvironmentConfig.bindAddress)
        .port(EnvironmentConfig.port)
    val server = WireMockServer(wireMockConfig)
    server.start()

    val headers = HttpHeaders(
        listOf(
            HttpHeader("Content-Type", "application/json"),
            HttpHeader("First-Custom-Header", "Wire"),
            HttpHeader("First-Custom-Header", "Mock")
        )
    )

    // Here we are adding new stubs

    server.givenThat(get(urlPathEqualTo("/lesson/2"))
        .atPriority(1) // <— higher priority than the other two stubs
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeaders(headers)
                .withBody("{\"message\": \"Hello from lesson_2\"}")
        )
    )

    server.givenThat(get(urlPathMatching("/lesson/[0-9]+$")) // <— regular expression to match any lesson number
        .atPriority(2)
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\": \"No matter what lesson number you request, you'll see me\"}")
        )
    )

    server.givenThat(get(urlPathMatching("/lesson/.*")) // <— regular expression to match anything that follows /lesson/
        .atPriority(3) // <— lowest priority
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\": \"No matter what follows /lesson you'll see me\"}")
        )
    )
}

// If you run the server and execute requests GET /lesson/2 that matches all three stubs, the one with higher priority will be returned.