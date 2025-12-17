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

    fun ensureTrafficTable(
        dynamo: DynamoDbClient,
        tableName: String,
    ) {
        if (tableExists(dynamo, tableName)) {
            return
        }

        dynamo.createTable(
            CreateTableRequest.builder()
                .tableName(tableName)
                // Attribute definitions for PK, SK, and GSI PK
                .attributeDefinitions(
                    AttributeDefinition.builder()
                        .attributeName("sessionId")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("timestamp")
                        .attributeType(ScalarAttributeType.N)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("id")
                        .attributeType(ScalarAttributeType.S)
                        .build()
                )
                .keySchema(
                    KeySchemaElement.builder()
                        .attributeName("sessionId")
                        .keyType(KeyType.HASH)
                        .build(),
                    KeySchemaElement.builder()
                        .attributeName("timestamp")
                        .keyType(KeyType.RANGE)
                        .build()
                )
                // Global Secondary Index on 'id'
                .globalSecondaryIndexes(
                    GlobalSecondaryIndex.builder()
                        .indexName("id-index")
                        .keySchema(
                            KeySchemaElement.builder()
                                .attributeName("id")
                                .keyType(KeyType.HASH)
                                .build()
                        )
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build()
        )

        waitUntilActive(dynamo, tableName)
    }

    fun ensureStubsTable(
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
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("stubId")
                        .attributeType(ScalarAttributeType.S)
                        .build()
                )
                .keySchema(
                    KeySchemaElement.builder()
                        .attributeName("sessionId")
                        .keyType(KeyType.HASH)
                        .build(),
                    KeySchemaElement.builder()
                        .attributeName("stubId")
                        .keyType(KeyType.RANGE)
                        .build()
                )
                .globalSecondaryIndexes(
                    GlobalSecondaryIndex.builder()
                        .indexName("stubId-index")
                        .keySchema(
                            KeySchemaElement.builder()
                                .attributeName("stubId")
                                .keyType(KeyType.HASH)
                                .build()
                        )
                        .projection(Projection.builder().projectionType(ProjectionType.KEYS_ONLY).build())
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
