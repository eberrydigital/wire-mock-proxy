package se.strawberry.infrastructure.dynamo

import se.strawberry.config.DynamoConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

object DynamoClientFactory {

    fun create(config: DynamoConfig): DynamoDbClient {
        val builder = DynamoDbClient.builder()
            .region(Region.of(config.region))

        if (config.endpoint != null) {
            builder.endpointOverride(config.endpoint)
        }

        if (config.accessKeyId != null && config.secretAccessKey != null) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        config.accessKeyId,
                        config.secretAccessKey
                    )
                )
            )
        }

        return builder.build()
    }
}
