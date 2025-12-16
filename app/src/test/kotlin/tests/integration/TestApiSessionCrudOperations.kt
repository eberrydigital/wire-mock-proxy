package tests.integration

import com.fasterxml.jackson.module.kotlin.readValue
import helpers.SessionApi
import helpers.SessionResponse
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import se.strawberry.common.Json
import se.strawberry.repository.RepositoryConstants.DYNAMO.SESSION_TABLE_NAME
import se.strawberry.repository.session.SessionRepository
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import tests.setup.BaseIntegrationTest

/**
 * Integration tests for Session Creation API
 * Tests the full flow: API -> DynamoDB container
 */
class TestApiSessionCrudOperations : BaseIntegrationTest() {

    @Test
    fun `should create session with valid ID and ACTIVE status`() {
        // Create session
        val apiResponse = SessionApi.createSession(http, apiBaseUrl())
        assertThat(apiResponse.code, equalTo(201))

        val body = apiResponse.body.string()
        val session = Json.mapper.readValue<SessionResponse>(body)

        // Verify session data
        assertThat(session.id, not(emptyString()))
        assertThat(session.status, equalTo(SessionRepository.Session.Status.ACTIVE.name))
        assertThat(session.createdAt, greaterThan(0L))

        // Verify the session exists in DynamoDB

        val dbItem = dynamoClient.getItem { builder ->
            builder.tableName(SESSION_TABLE_NAME).key(
                mapOf("sessionId" to AttributeValue.builder()
                    .s(session.id)
                    .build()
                )
            )
        }

        assertThat(dbItem.hasItem(), equalTo(true))
        assertThat(dbItem.item()["sessionId"]?.s(), equalTo(session.id))
        assertThat(dbItem.item()["status"]?.s(), equalTo(SessionRepository.Session.Status.ACTIVE.name))
    }

    @Test
    fun `should persist session to DynamoDB`() {
        // Create session
        val sessionId = SessionApi.createSession(http, apiBaseUrl())
            .use { response ->
                val body = response.body.string()
                Json.mapper.readValue<SessionResponse>(body).id
            }

        // Verify session can be retrieved
        SessionApi.getSession(http, apiBaseUrl(), sessionId)
            .use { response ->
                assertThat(response.code, equalTo(200))

                val body = response.body.string()
                val session = Json.mapper.readValue<SessionResponse>(body)

                assertThat(session.id, equalTo(sessionId))
                assertThat(session.status, equalTo(SessionRepository.Session.Status.ACTIVE.name))
            }
    }

    @Test
    fun `should create multiple sessions with unique IDs`() {
        val sessionIds = mutableSetOf<String>()
        val iterations = 5

        // Create 5 sessions
        repeat(iterations) {
            SessionApi.createSession(http, apiBaseUrl())
                .use { response ->
                    assertThat(response.code, equalTo(201))

                    val body = response.body.string()
                    val session = Json.mapper.readValue<SessionResponse>(body)

                    sessionIds.add(session.id)
                }
        }

        // Verify all IDs are unique
        assertThat(sessionIds.size, equalTo(iterations))

        // Verify all sessions are retrievable
        sessionIds.forEach { sessionId ->
            SessionApi.getSession(http, apiBaseUrl(), sessionId)
                .use { response ->
                    assertThat(response.code, equalTo(200))
                }
        }
    }

    @Test
    fun `should return 404 for non-existent session`() {
        val nonExistentSessionId = "non-existent-session-id"

        SessionApi.getSession(http, apiBaseUrl(), nonExistentSessionId).use { response ->
            assertThat(response.code, equalTo(404))
        }
    }

    @Test
    fun `should return 204 for closing session`() {
        val sessionId = SessionApi.createSession(http, apiBaseUrl())
            .use { response ->
                val body = response.body.string()
                Json.mapper.readValue<SessionResponse>(body).id
            }

        SessionApi.closeSession(http, apiBaseUrl(), sessionId).use { response ->
            assertThat(response.code, equalTo(204))
        }
    }
}

