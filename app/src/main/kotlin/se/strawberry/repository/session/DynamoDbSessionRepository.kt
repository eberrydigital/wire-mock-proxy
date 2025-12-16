package se.strawberry.repository.session

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import se.strawberry.repository.RepositoryConstants.DYNAMO.SESSION_TABLE_NAME
import se.strawberry.repository.session.SessionRepository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.ReturnValue
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest

class DynamoDbSessionRepository(
    private val dynamo: DynamoDbClient
) : SessionRepository {

    private val log: Logger = LoggerFactory.getLogger(DynamoDbSessionRepository::class.java)

    override fun create(session: SessionRepository.Session): Boolean {
        val item = toItem(session)
        val req = PutItemRequest.builder()
            .tableName(SESSION_TABLE_NAME)
            .item(item)
            .conditionExpression("attribute_not_exists(sessionId)")
            .build()
        return try {
            dynamo.putItem(req)
            true
        } catch (e: ConditionalCheckFailedException) {
            log.warn("Session already exists: ${session.id}")
            false
        }
    }

    override fun get(id: String): SessionRepository.Session? {
        val req = GetItemRequest.builder()
            .tableName(SESSION_TABLE_NAME)
            .key(mapOf("sessionId" to AttributeValue.builder().s(id).build()))
            .consistentRead(true)
            .build()
        val resp = dynamo.getItem(req)
        if (!resp.hasItem() || resp.item().isEmpty()) return null
        return fromItem(resp.item())
    }

    override fun close(id: String): Boolean {
        val now = System.currentTimeMillis()
        val req = UpdateItemRequest.builder()
            .tableName(SESSION_TABLE_NAME)
            .key(mapOf("sessionId" to AttributeValue.builder().s(id).build()))
            .updateExpression("SET #st = :closed, closedAt = :closedAt")
            .conditionExpression("attribute_exists(sessionId)")
            .expressionAttributeNames(mapOf("#st" to "status"))
            .expressionAttributeValues(
                mapOf(
                    ":closed" to AttributeValue.builder().s(SessionRepository.Session.Status.CLOSED.name).build(),
                    ":closedAt" to AttributeValue.builder().n(now.toString()).build(),
                )
            )
            .returnValues(ReturnValue.NONE)
            .build()
        return try {
            dynamo.updateItem(req)
            true
        } catch (e: ConditionalCheckFailedException) {
            false
        }
    }

    private fun toItem(s: SessionRepository.Session): Map<String, AttributeValue> {
        val m = mutableMapOf<String, AttributeValue>()
        m["sessionId"] = AttributeValue.builder().s(s.id).build()
        s.name?.let { m["name"] = AttributeValue.builder().s(it).build() }
        s.owner?.let { m["owner"] = AttributeValue.builder().s(it).build() }
        m["createdAt"] = AttributeValue.builder().n(s.createdAt.toString()).build()
        s.expiresAt?.let { m["expiresAt"] = AttributeValue.builder().n(it.toString()).build() }
        m["status"] = AttributeValue.builder().s(s.status.name).build()
        return m
    }

    private fun fromItem(item: Map<String, AttributeValue>): SessionRepository.Session {
        val id = item["sessionId"]?.s() ?: throw IllegalStateException("Missing sessionId")
        val name = item["name"]?.s()
        val owner = item["owner"]?.s()
        val createdAt = item["createdAt"]?.n()?.toLongOrNull() ?: 0L
        val expiresAt = item["expiresAt"]?.n()?.toLongOrNull()
        val statusRaw = item["status"]?.s() ?: SessionRepository.Session.Status.ACTIVE.name
        val status = runCatching { SessionRepository.Session.Status.valueOf(statusRaw) }
            .getOrElse { SessionRepository.Session.Status.ACTIVE }
        return SessionRepository.Session(
            id = id,
            name = name,
            owner = owner,
            createdAt = createdAt,
            expiresAt = expiresAt,
            status = status,
        )
    }
}