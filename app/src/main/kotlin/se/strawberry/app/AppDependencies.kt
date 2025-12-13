package se.strawberry.app

import DynamoSessionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import se.strawberry.common.Json
import se.strawberry.repository.session.SessionRepository
import se.strawberry.infrastructure.dynamo.DynamoClientFactory
import se.strawberry.service.stub.StubService
import se.strawberry.service.stub.StubServiceImpl
import se.strawberry.service.request.RequestService
import se.strawberry.service.request.RequestServiceImpl
import se.strawberry.service.wiremock.ServerWireMockClient
import se.strawberry.service.wiremock.WireMockClient
import se.strawberry.config.AppConfig

data class AppDependencies(
    val mapper: ObjectMapper,
    val wireMockClient: WireMockClient,
    val stubService: StubService,
    val requestService: RequestService,
    val sessionRepository: SessionRepository
)

fun buildDependencies(cfg: AppConfig): AppDependencies {
    val mapper = Json.mapper
    val wireMockClient: WireMockClient = ServerWireMockClient()
    val stubService: StubService = StubServiceImpl(mapper, wireMockClient)
    val requestService: RequestService = RequestServiceImpl(mapper, wireMockClient)

    val dynamo = DynamoClientFactory.create(cfg.dynamo)
    val sessionRepository: SessionRepository = DynamoSessionRepository(dynamo, cfg.dynamo.sessionsTable)

    return AppDependencies(
        mapper = mapper,
        wireMockClient = wireMockClient,
        stubService = stubService,
        requestService = requestService,
        sessionRepository = sessionRepository
    )
}
