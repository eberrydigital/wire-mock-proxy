package se.strawberry.di

import org.koin.dsl.module
import se.strawberry.service.request.RequestService
import se.strawberry.service.request.RequestServiceImpl
import se.strawberry.service.stub.StubService
import se.strawberry.service.stub.StubServiceImpl
import se.strawberry.service.traffic.TrafficBroadcastService
import se.strawberry.service.traffic.TrafficPersister
import se.strawberry.service.traffic.WebSocketTrafficBroadcaster
import se.strawberry.service.wiremock.ServerWireMockClient
import se.strawberry.service.wiremock.WireMockClient

val serviceModule = module {
    single<TrafficBroadcastService> { WebSocketTrafficBroadcaster(get()) }
    single { TrafficPersister(get(), get()) }
    single<WireMockClient> { ServerWireMockClient(get()) }
    single<StubService> { StubServiceImpl(get(), get(), get()) }
    single<RequestService> { RequestServiceImpl(get(), get()) }
}
