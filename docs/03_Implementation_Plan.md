# Implementation Plan (Refactored)

## Phase 1: Traffic Persistence
**Goal**: Persist traffic to DynamoDB (async) and disable WireMock memory journal.

1. **Commit 1**.1: `RecordedRequest` Model & `RecordedRequestRepository` Interface.
2. **Commit 1.2**: `DynamoDbRecordedRequestRepository` (Put/Query with truncation).
3. **Commit 1.3**: Async Pipeline (`TrafficCaptureListener` -> `Channel` -> `TrafficPersister`).
4. **Commit 1.4**: Switch `RequestServiceImpl` to Repository.
5. **Commit 1.5**: Config cleanup (Disable WireMock journal).

## Phase 2: Stub Persistence
**Goal**: Persist stubs to DynamoDB and sync with WireMock.

1. **Commit 2.1**: `StubRepository` & `DynamoDbStubRepository`.
2. **Commit 2.2**: `StubServiceImpl` refactor.
   - `create`: DB -> WireMock.
   - `delete`: DB -> WireMock.
   - `init`: DB Load -> WireMock.

## Phase 3: WebSockets (Real-time)
**Goal**: Push traffic events to frontend.

1. **Commit 3.1**: Ktor WebSocket Setup (`install(WebSockets)`).
2. **Commit 3.2**: `TrafficBroadcaster`.
   - Connects to the same `Channel` as Phase 1 or uses `SharedFlow`.
   - `broadcast(event)` -> sends to all explicit `sessionId` subscribers.
3. **Commit 3.3**: WebSocket Route `/ws/sessions/{id}`.
   - On connect: Subscribe to topics.
   - On message: (Keepalive / Ping).

## Phase 4: Logic Hardening
**Goal**: Ensure proxy only allows ACTIVE sessions.

1. **Commit 4.1**: `SessionCache` (Optional but recommended for performance) or direct DB check.
2. **Commit 4.2**: Update `DynamicRoutingGuard`.
   - Inject `SessionRepository`.
   - Check `repo.get(sessionId)?.status == ACTIVE`.
   - Return 403 if invalid.

## Verification
- **Unit**: Repositories, Service Logic.
- **Integration**:
  - `TestTrafficPersistence`: Verify DDB write.
  - `TestWebSockets`: Connect WS client, proxy request, verify frame received.
  - `TestSecurity`: Try proxying with invalid session, verify 403.
