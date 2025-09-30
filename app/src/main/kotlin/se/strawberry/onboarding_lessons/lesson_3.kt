package se.strawberry.onboarding_lessons

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders

// -- Request Matching --

// Starting from this lesson, every lesson will be a continuation of the previous one.
// So, that you could track the changes and development of your WireMock project.
// We also are going to use EnvironmentConfig instead of using hardcoded values.
// We'll use regular expression so that any lesson number will match the stub.

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
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeaders(headers)
                .withBody("{\"message\": \"Welcome to lesson_2\"}") // <- Pay attention that this body is never returned anymore; we'll address that in the next lesson
        )
    )

    server.givenThat(get(urlPathMatching("/lesson/[0-9]+$")) // <— regular expression to match any lesson number
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\": \"No matter what lesson number you request, you'll see me\"}")
        )
    )
}