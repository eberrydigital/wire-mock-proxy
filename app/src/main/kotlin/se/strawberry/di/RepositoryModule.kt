package se.strawberry.di

import org.koin.dsl.module
import se.strawberry.repository.session.DynamoDbSessionRepository
import se.strawberry.repository.session.SessionRepository
import se.strawberry.repository.stub.DynamoDbStubRepository
import se.strawberry.repository.stub.StubRepository
import se.strawberry.repository.traffic.DynamoDbRecordedRequestRepository
import se.strawberry.repository.traffic.RecordedRequestRepository

val repositoryModule = module {
    single<SessionRepository> { DynamoDbSessionRepository(get()) }
    single<RecordedRequestRepository> { DynamoDbRecordedRequestRepository(get()) }
    single<StubRepository> { DynamoDbStubRepository(get()) }
}