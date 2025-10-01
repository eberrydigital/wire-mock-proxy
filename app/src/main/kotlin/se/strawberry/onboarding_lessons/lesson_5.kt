package se.strawberry.onboarding_lessons

import EnvironmentConfig
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import java.net.HttpURLConnection.HTTP_ACCEPTED
import java.net.HttpURLConnection.HTTP_OK

// -- Mocking Bearer Token for Authentication --

// In this lesson we will imitate authorization by Bearer token in the Authorization header.
// Also pay attention that we changed our status codes to global constants for better readability.

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
                .withStatus(HTTP_OK)
                .withHeaders(headers)
                .withBody("{\"message\": \"Hello from lesson_2\"}")
        )
    )

    server.givenThat(get(urlPathMatching("/lesson/[0-9]+$"))
        .atPriority(2)
        .willReturn(
            aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\": \"No matter what lesson number you request, you'll see me\"}")
        )
    )

    server.givenThat(get(urlPathMatching("/lesson/.*"))
        .atPriority(3) // <— lowest priority
        .willReturn(
            aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\": \"No matter what follows /lesson you'll see me\"}")
        )
    )

    server.givenThat(
        get(urlPathMatching("/login"))
            .withHeader("Authorization", equalTo("Bearer 098asdafas-023213-dsasda-1131123123123"))
            .willReturn(
                aResponse()
                    .withStatus(HTTP_ACCEPTED)
                    .withHeader("Content-Type", "text/plain")
                    .withBody("Login Successful")
            )
    )
}
