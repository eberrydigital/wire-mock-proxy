package se.strawberry.service.stub

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import se.strawberry.common.Headers
import se.strawberry.common.MetadataKeys
import se.strawberry.api.models.stub.CreateStubRequest
import se.strawberry.helpers.SessionHelper
import se.strawberry.service.wiremock.WireMockClient
import se.strawberry.wiremock.StubBuilder
import se.strawberry.repository.stub.StubRepository
import se.strawberry.repository.stub.StubRepository.Stub
import org.slf4j.LoggerFactory

class StubServiceImpl(
    private val mapper: ObjectMapper,
    private val wireMockClient: WireMockClient,
    private val repository: StubRepository
) : StubService {
    
    private val log = LoggerFactory.getLogger(javaClass)

    override fun create(dto: CreateStubRequest, sessionId: String): Response {
        val patchedDto = SessionHelper.withSessionMatch(dto, sessionId)
        val stubMapping = StubBuilder.buildStubMapping(patchedDto)
        
        // 1. Save to DynamoDB
        val md = stubMapping.metadata ?: com.github.tomakehurst.wiremock.common.Metadata(mutableMapOf<String, Any>())
        // Add session ID to metadata for easier retrieval
        val mutableMd = md.toMutableMap()
        mutableMd["sessionId"] = sessionId
        stubMapping.metadata = com.github.tomakehurst.wiremock.common.Metadata(mutableMd)
        
        val usesLeft: Int? = mutableMd[MetadataKeys.REMAINING_USES] as? Int ?: (mutableMd[MetadataKeys.REMAINING_USES] as? Number)?.toInt()
        val expiresAt: Long? = mutableMd[MetadataKeys.EXPIRES_AT] as? Long ?: (mutableMd[MetadataKeys.EXPIRES_AT] as? Number)?.toLong()
        
        val stubEntity = Stub(
            sessionId = sessionId,
            stubId = stubMapping.id.toString(),
            mappingJson = StubMapping.buildJsonStringFor(stubMapping),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            expiresAt = expiresAt,
            usesLeft = usesLeft,
            status = Stub.Status.ACTIVE
        )
        
        try {
            repository.save(stubEntity)
        } catch (e: Exception) {
            log.error("Failed to persist stub ${stubMapping.id}", e)
            return json(500, """{"error":"persistence_failed"}""")
        }

        // 2. Add to WireMock (Memory)
        wireMockClient.addStub(stubMapping)

        val payload = mapper.writeValueAsString(
            mapOf(
                "id" to stubMapping.id,
                "summary" to "${dto.request.method} ${dto.request.url.value}",
                "usesLeft" to usesLeft,
                MetadataKeys.EXPIRES_AT to expiresAt,
                "sessionId" to sessionId
            )
        )
        return json(201, payload)
    }

    override fun list(): Response {
        // We list from WireMock to reflect current active state (including decremented usage counts)
        val list = wireMockClient.listStubs().map { sm ->
            val md = sm.metadata
            mapOf(
                "id" to sm.id,
                "priority" to sm.priority,
                "request" to sm.request?.url,
                "method" to sm.request?.method?.value(),
                "usesLeft" to md?.let { (it[MetadataKeys.REMAINING_USES] as? Number)?.toInt() },
                MetadataKeys.EXPIRES_AT to md?.let { (it[MetadataKeys.EXPIRES_AT] as? Number)?.toLong() }
            )
        }
        return json(200, mapper.writeValueAsString(list))
    }

    override fun delete(id: String): Response {
        // 1. Try to find in WireMock memory (Primary/Fastest)
        val memStub = wireMockClient.listStubs().firstOrNull { it.id.toString() == id }
        
        var sessionId = memStub?.metadata?.getString("sessionId") ?: memStub?.metadata?.get("sessionId") as? String

        // 2. Fallback: Query DB GSI if not found in memory or metadata missing
        if (sessionId == null) {
             val dbStub = repository.findByStubId(id)
             if (dbStub != null) {
                 sessionId = dbStub.sessionId
             }
        }
        
        if (sessionId == null) {
            // Not found in memory with session, nor in DB.
            log.warn("Stub $id not found or missing session info")
            return json(404, """{"error":"not_found"}""") 
        }

        // 3. Delete from system
        repository.delete(sessionId, id)
        
        // Remove from WireMock if it was found there
        if (memStub != null) {
            wireMockClient.removeStub(memStub)
        } else {
             // If we found it in DB but not memory, it might be expired or not loaded. 
             // We can try to remove by ID blindly from WireMock just in case.
             // But WireMock removeStub requires an object. removeStubMapping(StubMapping)
             // We can construct a dummy mapping with ID? Or use client.removeStub(id)? 
             // Client interface takes StubMapping.
             // We can ignore if not in memory.
        }

        return Response.response()
            .status(204)
            .build()
    }
            
    override fun syncFromDb() {
        // Retrieve all active stubs and load into WireMock
        val activeStubs = repository.getAllActive()
        log.info("Syncing ${activeStubs.size} stubs from DB to WireMock")
        activeStubs.forEach { stubEntity ->
            try {
                val stubMapping = StubMapping.buildFrom(stubEntity.mappingJson)
                wireMockClient.addStub(stubMapping)
            } catch (e: Exception) {
                log.error("Failed to parse/load stub ${stubEntity.stubId}", e)
            }
        }
    }

    private fun json(code: Int, body: String): Response =
        Response.response()
            .status(code)
            .headers(HttpHeaders(HttpHeader.httpHeader(Headers.CONTENT_TYPE, Headers.JSON)))
            .body(body)
            .build()
}