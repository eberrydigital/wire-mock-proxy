package se.strawberry.di

import org.koin.dsl.module
import se.strawberry.wiremock.WireMockBootstrap
import se.strawberry.wiremock.WireMockLifecycle

val wiremockRuntimeModule = module {
    single { WireMockLifecycle(get()) }
    single { WireMockBootstrap(get()) }
}
