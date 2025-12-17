package se.strawberry.app

import com.fasterxml.jackson.databind.ObjectMapper
import se.strawberry.common.Json
import se.strawberry.repository.session.SessionRepository
import se.strawberry.repository.session.DynamoDbSessionRepository
import se.strawberry.infrastructure.dynamo.DynamoClientFactory
import se.strawberry.service.stub.StubService
import se.strawberry.service.stub.StubServiceImpl
import se.strawberry.service.request.RequestService
import se.strawberry.service.request.RequestServiceImpl
import se.strawberry.service.wiremock.ServerWireMockClient
import se.strawberry.service.wiremock.WireMockClient
import se.strawberry.config.AppConfig

import se.strawberry.repository.traffic.RecordedRequestRepository
import se.strawberry.repository.traffic.DynamoDbRecordedRequestRepository
import se.strawberry.service.traffic.TrafficPersister
import se.strawberry.repository.stub.StubRepository
import se.strawberry.repository.stub.DynamoDbStubRepository

data class AppDependencies(
    val mapper: ObjectMapper,
    val wireMockClient: WireMockClient,
    val stubService: StubService,
    val requestService: RequestService,
    val sessionRepository: SessionRepository,
    val trafficPersister: TrafficPersister,
    val trafficBroadcastService: se.strawberry.service.traffic.TrafficBroadcastService
)

fun buildDependencies(cfg: AppConfig): AppDependencies {
    val mapper = Json.mapper
    val wireMockClient: WireMockClient = ServerWireMockClient()
    val dynamo = DynamoClientFactory.create(cfg.dynamo)

    val sessionRepository: SessionRepository = DynamoDbSessionRepository(dynamo)
    val recordedRequestRepository: RecordedRequestRepository = DynamoDbRecordedRequestRepository(dynamo)
    
    val trafficBroadcaster = se.strawberry.service.traffic.WebSocketTrafficBroadcaster(mapper)
    val trafficPersister = TrafficPersister(recordedRequestRepository, trafficBroadcaster)
    val stubRepository: StubRepository = DynamoDbStubRepository(dynamo)

    val stubService: StubService = StubServiceImpl(mapper, wireMockClient, stubRepository)
    val requestService: RequestService = RequestServiceImpl(mapper, recordedRequestRepository)

    return AppDependencies(
        mapper = mapper,
        wireMockClient = wireMockClient,
        stubService = stubService,
        requestService = requestService,
        sessionRepository = sessionRepository,
        trafficPersister = trafficPersister,
        trafficBroadcastService = trafficBroadcaster
    )
}
