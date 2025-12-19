package se.strawberry.di

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.koin.dsl.module
import se.strawberry.config.AppConfig
import se.strawberry.wiremock.filters.DynamicRoutingGuard
import se.strawberry.wiremock.listeners.EphemeralServeEventListener
import se.strawberry.wiremock.listeners.TrafficCaptureListener
import se.strawberry.wiremock.matchers.TtlGuardMatcher
import se.strawberry.wiremock.templating.ServiceTemplateHelpers

/**
 * WireMock extensions (listeners/matchers/helpers).
 * These may depend on repositories/services and should NOT be constructed inside WireMockServer builder.
 */
val wiremockExtensionsModule = module {

    single { TtlGuardMatcher() }

    single { EphemeralServeEventListener(get()) }

    single { ServiceTemplateHelpers(get<AppConfig>().services) }

    // Your current listener only needs TrafficPersister (based on your snippet)
    single { TrafficCaptureListener(get()) }

    // Needs cfg + sessionRepository
    single {
        val cfg = get<AppConfig>()
        DynamicRoutingGuard(cfg.services, cfg.allowedPorts, get())
    }
}

/**
 * WireMockServer itself (infrastructure).
 * Only assembles extensions that are already created by DI.
 */
val wireMockServerModule = module {
    single {
        val cfg = get<AppConfig>()

        val extensions = arrayOf(
            get<DynamicRoutingGuard>(),
            get<TtlGuardMatcher>(),
            get<EphemeralServeEventListener>(),
            get<TrafficCaptureListener>(),
            get<ServiceTemplateHelpers>(),
        )

        WireMockServer(
            options()
                .port(cfg.wireMockServerPort)
                .bindAddress(cfg.hostAddress)
                .templatingEnabled(true)
                .disableRequestJournal()
                .extensions(*extensions)
        )
    }
}
