package se.strawberry.config

import java.net.URI

/**
 * DynamoDB configuration.
 *
 * For local development you typically set:
 * - DYNAMO_ENDPOINT=http://localhost:8000
 * - AWS_REGION=eu-north-1
 * - AWS_ACCESS_KEY_ID=local
 * - AWS_SECRET_ACCESS_KEY=local
 */
data class DynamoConfig(
    /** Optional endpoint override (used for local dynamodb-local). */
    val endpoint: URI? = null,
    /** AWS region (required by SDK even for local). */
    val region: String = "eu-north-1",
    /** Credentials (dummy values are OK for local). */
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
)
