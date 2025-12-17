package se.strawberry.repository.stub

import se.strawberry.repository.stub.StubRepository.Stub
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import org.slf4j.LoggerFactory

class DynamoDbStubRepository(
    private val dynamoDb: DynamoDbClient,
    private val tableName: String = "stubs" // Should match RepositoryConstants.STUB_TABLE_NAME
) : StubRepository {
    
    private val log = LoggerFactory.getLogger(javaClass)

    override fun save(stub: Stub) {
        val item = mutableMapOf(
            "sessionId" to s(stub.sessionId),
            "stubId" to s(stub.stubId),
            "mappingJson" to s(stub.mappingJson),
            "createdAt" to n(stub.createdAt),
            "updatedAt" to n(stub.updatedAt),
            "status" to s(stub.status.name)
        )
        if (stub.expiresAt != null) item["expiresAt"] = n(stub.expiresAt)
        if (stub.usesLeft != null) item["usesLeft"] = n(stub.usesLeft)

        try {
            dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build())
        } catch (e: Exception) {
            log.error("Failed to save stub ${stub.sessionId}:${stub.stubId}", e)
            throw e
        }
    }

    override fun get(sessionId: String, stubId: String): Stub? {
        val key = mapOf(
            "sessionId" to s(sessionId),
            "stubId" to s(stubId)
        )
        try {
            val response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build())
            if (!response.hasItem()) return null
            return toEntity(response.item())
        } catch (e: Exception) {
            log.error("Failed to get stub $sessionId:$stubId", e)
            return null
        }
    }

    override fun listBySession(sessionId: String): List<Stub> {
         try {
            val response = dynamoDb.query(QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("sessionId = :pk")
                .expressionAttributeValues(mapOf(":pk" to s(sessionId)))
                .build())
            return response.items().map { toEntity(it) }
        } catch (e: Exception) {
             log.error("Failed to list stubs for session $sessionId", e)
            return emptyList()
        }
    }

    override fun delete(sessionId: String, stubId: String) {
         val key = mapOf(
            "sessionId" to s(sessionId),
            "stubId" to s(stubId)
        )
        try {
            dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build())
        } catch (e: Exception) {
            log.error("Failed to delete stub $sessionId:$stubId", e)
        }
    }

    override fun findByStubId(stubId: String): Stub? {
        try {
            val response = dynamoDb.query(QueryRequest.builder()
                .tableName(tableName)
                .indexName("stubId-index")
                .keyConditionExpression("stubId = :id")
                .expressionAttributeValues(mapOf(":id" to s(stubId)))
                .limit(1)
                .build())
            
            if (response.count() == 0) return null
            
            // GSI projects "KEYS_ONLY" by default in my bootstrap, but I need full item?
            // Wait, if I project KEYS_ONLY, I only get stubId and sessionId.
            // That IS enough to call get(sessionId, stubId) or just return a Stub with those fields if all I need is sessionId.
            // But verify: DynamoBootstrap specified KEYS_ONLY.
            // So return value will only have keys.
            // I should fetch the full item using the keys if I return "Stub", OR just return what I have.
            // For now, let's fetch full item to be safe and consistent with "get" contract.
            
            val item = response.items().first()
            val sessionId = item["sessionId"]?.s() ?: return null
            return get(sessionId, stubId)
        } catch (e: Exception) {
            log.error("Failed to find stub by id $stubId", e)
            return null
        }
    }
    
    override fun getAllActive(): List<Stub> {
        // Startup sync. Full scan required given current schema/requirements.
        // In prod this might be heavy, but efficient enough for now or we might add GSI later if "all active" is frequent.
        // Actually, this is only called on startup.
        try {
            val response = dynamoDb.scan(ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#s = :active")
                .expressionAttributeNames(mapOf("#s" to "status"))
                .expressionAttributeValues(mapOf(":active" to s(Stub.Status.ACTIVE.name)))
                .build())
            return response.items().map { toEntity(it) }
        } catch (e: Exception) {
            log.error("Failed to scan active stubs", e)
            return emptyList()
        }
    }

    private fun toEntity(item: Map<String, AttributeValue>): Stub {
        return Stub(
            sessionId = item["sessionId"]?.s() ?: "",
            stubId = item["stubId"]?.s() ?: "",
            mappingJson = item["mappingJson"]?.s() ?: "{}",
            createdAt = item["createdAt"]?.n()?.toLong() ?: 0L,
            updatedAt = item["updatedAt"]?.n()?.toLong() ?: 0L,
            expiresAt = item["expiresAt"]?.n()?.toLong(),
            usesLeft = item["usesLeft"]?.n()?.toInt(),
            status = try {
                Stub.Status.valueOf(item["status"]?.s() ?: "ACTIVE")
            } catch (_: Exception) { Stub.Status.ACTIVE }
        )
    }

    private fun s(value: String): AttributeValue = AttributeValue.builder().s(value).build()
    private fun n(value: Number): AttributeValue = AttributeValue.builder().n(value.toString()).build()
}
