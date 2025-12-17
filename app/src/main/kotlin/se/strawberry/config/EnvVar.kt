package se.strawberry.config

import se.strawberry.repository.RepositoryConstants.DYNAMO.SESSION_TABLE_NAME

/**
 * Registry of all environment variables used in the application.
 * This prevents typos and provides type safety.
 */
sealed class EnvVar<T>(val key: String, val default: T? = null) {

    // Application ports and network
    data object WireMockServerPort : EnvVar<Int>("WIREMOCK_SERVER_PORT", 8222)
    data object KtorApiPort : EnvVar<Int>("KTOR_API_PORT", 8333)
    data object HostAddress : EnvVar<String>("HOST_ADDRESS", "0.0.0.0")

    // Proxy configuration
    data object DynAllowedPorts : EnvVar<String>("DYN_ALLOWED_PORTS", "80,443")
    data object ServiceMap : EnvVar<String>("SERVICE_MAP")

    // DynamoDB configuration
    data object DynamoEndpoint : EnvVar<String>("DYNAMO_ENDPOINT")
    data object AwsRegion : EnvVar<String>("AWS_REGION", "eu-north-1")
    data object DynamoSessionsTable : EnvVar<String>("DYNAMO_SESSIONS_TABLE", SESSION_TABLE_NAME)
    data object AwsAccessKeyId : EnvVar<String>("AWS_ACCESS_KEY_ID")
    data object AwsSecretAccessKey : EnvVar<String>("AWS_SECRET_ACCESS_KEY")
}

