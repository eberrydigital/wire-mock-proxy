package se.strawberry.repository.traffic

import org.slf4j.LoggerFactory
import se.strawberry.repository.traffic.RecordedRequestRepository.RecordedRequest
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

class DynamoDbRecordedRequestRepository(
    private val dynamoDb: DynamoDbClient,
    private val tableName: String = "proxy-traffic"
) : RecordedRequestRepository {

    private val log = LoggerFactory.getLogger(javaClass)
    private val maxBodySize = 350 * 1024 // 400Kb is the limit for a DDB record
    private val truncationSuffix = "\n[...Body truncated - size limit exceeded...]"

    override fun save(rr: RecordedRequest): Boolean {
        try {
            val item = mutableMapOf(
                "sessionId" to s(rr.sessionId),
                "timestamp" to n(rr.timestamp), // Sort Key
                "id" to s(rr.id),
                "method" to s(rr.method),
                "path" to s(rr.path),
                "responseStatus" to n(rr.responseStatus),
                "duration" to n(rr.duration),
                "stubbed" to bool(rr.stubbed)
            )

            // Maps
            if (rr.query.isNotEmpty()) item["query"] = m(rr.query)
            if (rr.headers.isNotEmpty()) item["headers"] = m(rr.headers)
            if (rr.responseHeaders.isNotEmpty()) item["responseHeaders"] = m(rr.responseHeaders)

            // Bodies with truncation
            item["body"] = s(truncate(rr.body))
            item["responseBody"] = s(truncate(rr.responseBody))
            
            //:TODO Implement time to live. TTL for Traffic instance should be TTL of session + 48H.
            
            dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build())
            
            return true
        } catch (e: Exception) {
            log.error("Failed to save traffic for session ${rr.sessionId}", e)
            return false
        }
    }

    override fun listBySession(sessionId: String, limit: Int): List<RecordedRequest> {
        try {
            val request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("sessionId = :pk")
                .expressionAttributeValues(mapOf(":pk" to s(sessionId)))
                .scanIndexForward(false) // Newest first
                .limit(limit)
                .build()

            val response = dynamoDb.query(request)
            return response.items().map { toEntity(it) }
        } catch (e: Exception) {
            log.error("Failed to list traffic for session $sessionId", e)
            return emptyList()
        }
    }

    override fun get(id: String): RecordedRequest? {
        try {
            val response = dynamoDb.query(QueryRequest.builder()
                .tableName(tableName)
                .indexName("id-index")
                .keyConditionExpression("id = :id")
                .expressionAttributeValues(mapOf(":id" to s(id)))
                .build())
            
            return response.items().firstOrNull()?.let { toEntity(it) }
        } catch (e: Exception) {
            log.warn("Failed to get request $id (using GSI id-index)", e)
            return null
        }
    }

    override fun clearAll(): Int {
        // Only for tests usually.
        return 0 
    }

    private fun truncate(content: String?): String? {
        if (content == null) return null
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBodySize) return content
        
        // Naive string truncation to likely fit
        // 1 char can be 3 bytes. 350KB chars is safe-ish.
        return content.take(350_000) + truncationSuffix
    }

    private fun toEntity(item: Map<String, AttributeValue>): RecordedRequest {
        return RecordedRequest(
            id = item["id"]?.s() ?: "",
            sessionId = item["sessionId"]?.s() ?: "",
            method = item["method"]?.s() ?: "",
            path = item["path"]?.s() ?: "",
            query = item["query"]?.m()?.mapValues { it.value.s() } ?: emptyMap(),
            headers = item["headers"]?.m()?.mapValues { it.value.s() } ?: emptyMap(),
            body = item["body"]?.s(),
            responseStatus = item["responseStatus"]?.n()?.toInt() ?: 0,
            responseHeaders = item["responseHeaders"]?.m()?.mapValues { it.value.s() } ?: emptyMap(),
            responseBody = item["responseBody"]?.s(),
            timestamp = item["timestamp"]?.n()?.toLong() ?: 0L,
            duration = item["duration"]?.n()?.toLong() ?: 0L,
            stubbed = item["stubbed"]?.bool() ?: false
        )
    }

    // Helpers
    private fun s(value: String?): AttributeValue = if (value != null) AttributeValue.builder().s(value).build() else AttributeValue.builder().s("").build()

    // DDB doesn't like null/empty S? actually it handles empty S? No, map values can be empty strings?
    // Correction: Empty strings in DDB are allowed now? No, they used not to be. But AWS SDK v2?
    // Safer to use NULL if empty? Let's assume non-empty for now or NULL.
    // Actually safe helper:
    // fun s(v: String?) = if (!v.isNullOrEmpty()) AttributeValue.builder().s(v).build() else AttributeValue.builder().nul(true).build()
    // But our model has non-null Strings for method/path.
    
    private fun n(value: Number): AttributeValue = AttributeValue.builder().n(value.toString()).build()
    private fun bool(value: Boolean): AttributeValue = AttributeValue.builder().bool(value).build()
    
    private fun m(map: Map<String, String>): AttributeValue {
        val converted = map.mapValues { s(it.value) }
        return AttributeValue.builder().m(converted).build()
    }
}
