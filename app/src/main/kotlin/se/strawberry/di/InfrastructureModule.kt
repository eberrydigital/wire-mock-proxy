package se.strawberry.di

import org.koin.dsl.module
import se.strawberry.common.Json
import se.strawberry.config.AppConfig
import se.strawberry.infrastructure.dynamo.DynamoClientFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

val infraModule = module {
    single { Json.mapper }
    single<DynamoDbClient> { DynamoClientFactory.create(get<AppConfig>().dynamo) }
}