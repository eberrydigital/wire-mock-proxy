package se.strawberry.infrastructure.dynamo

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

object DynamoBootstrap {

    fun ensureSessionsTable(
        dynamo: DynamoDbClient,
        tableName: String,
    ) {
        if (tableExists(dynamo, tableName)) {
            return
        }

        dynamo.createTable(
            CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(
                    AttributeDefinition.builder()
                        .attributeName("sessionId")
                        .attributeType(ScalarAttributeType.S)
                        .build()
                )
                .keySchema(
                    KeySchemaElement.builder()
                        .attributeName("sessionId")
                        .keyType(KeyType.HASH)
                        .build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build()
        )

        waitUntilActive(dynamo, tableName)
    }

    private fun tableExists(dynamo: DynamoDbClient, tableName: String): Boolean =
        try {
            dynamo.describeTable(
                DescribeTableRequest.builder()
                    .tableName(tableName)
                    .build()
            )
            true
        } catch (e: ResourceNotFoundException) {
            false
        }

    private fun waitUntilActive(dynamo: DynamoDbClient, tableName: String) {
        repeat(25) {
            val status = dynamo.describeTable(
                DescribeTableRequest.builder()
                    .tableName(tableName)
                    .build()
            ).table().tableStatus()

            if (status == TableStatus.ACTIVE) {
                return
            }

            Thread.sleep(200)
        }

        error("DynamoDB table '$tableName' did not become ACTIVE in time")
    }
}
