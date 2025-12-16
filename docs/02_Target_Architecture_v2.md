# Target Architecture Design (Split-plane: WireMock proxy + Ktor management)

This architecture keeps the current “split”:
- **WireMock** is the proxy runtime and executes stub/proxy decisions on the proxy port.
- **Ktor** is the management API (sessions/traffic/stubs) and provides WebSocket streaming on the API port.
- **DynamoDB** is the system of record for sessions, traffic, and stubs.
- **WireMock mappings** are a *runtime projection* of persisted stubs.

## Goals

- Enforce session policies on the proxy path (ACTIVE only).
- Persist **traffic metadata + body previews (not full bodies)** and stubs to DynamoDB.
- Support **real-time traffic streaming (WebSocket)** using the same preview-safe event contract.
- Avoid persisting **binary bodies**; store only `contentType` + `bodySize` for non-text payloads.
- Keep the design simple and maintainable for active development.
- Keep a **scalable upgrade path** to full-body capture via external blob storage pointers (without breaking the API/event contract).

## High-level architecture

### Modules / boundaries

1) **domain**
- Entities:
  - `Session`
  - `TrafficEvent`
  - `Stub`
- Pure rules/policies:
  - `Session.canAcceptTraffic(now)`
  - `Session.isReadOnly(now)`
  - `Stub.isActive(now)` (status + ttl + uses)

**TrafficEvent (preview-safe contract)**  
Traffic events are designed to be safe-by-default for persistence and streaming:
- Always captured:
  - request/response metadata (method, url/path, status), duration, target service, stubbed flag
  - `requestContentType`, `responseContentType`
  - `requestBodySize`, `responseBodySize`
  - `isBinaryRequest`, `isBinaryResponse`
  - `requestBodyTruncated`, `responseBodyTruncated`
- Captured only for text-like payloads:
  - `requestBodyPreview` (≤ N bytes)
  - `responseBodyPreview` (≤ N bytes)
- Future scalability fields (nullable, not required initially):
  - `requestBodyRef`, `responseBodyRef` (e.g., S3 key) for full-body on-demand retrieval later

2) **application (use-cases)**
- Sessions:
  - `CreateSession`, `GetSession`, `CloseSession`, `ExpireSessionIfNeeded`
- Traffic:
  - `RecordTrafficEvent`, `ListTraffic`, `GetTraffic`, `ClearTraffic`
- Stubs:
  - `CreateStub`, `ListStubs`, `DeleteStub`
- Startup projection:
  - `LoadActiveStubsIntoWireMock`

3) **adapters**
- **Ktor API adapter**
  - REST endpoints (`/_proxy-api/**`)
  - WebSocket endpoint for live traffic per session
- **WireMock adapters**
  - Request filter: validates headers + session ACTIVE using repository (with cache)
  - ServeEvent listener: transforms serve-events → **preview-safe** `TrafficEvent` and publishes to:
    - Dynamo traffic repository
    - WebSocket broadcaster
  - Admin client wrapper: create/delete mappings
- **DynamoDB adapters**
  - `SessionRepositoryDynamo`
  - `TrafficRepositoryDynamo`
  - `StubRepositoryDynamo`

4) **bootstrap**
- Manual DI composition (keep it simple)
- Start WireMock with injected filter/listener instances
- Start Ktor with injected use-cases and broadcaster

## Request lifecycle and data flow

### A) Proxy request (WireMock port)

1. Client sends request to proxy port with:
   - `X-Mock-Target-Service`
   - `X-Mock-Session-Id`

2. **WireMock Request Filter**
   - Validate:
     - service exists in `SERVICE_MAP`
     - port allowed
   - Enforce session rules:
     - load session from `SessionRepository`
     - `status == ACTIVE`
     - not expired (`now <= expiresAt`)
   - If invalid → return JSON error (no upstream call)

3. WireMock routing decision:
   - Matching stub mapping → return stubbed response
   - Else → proxy to upstream target service

4. **ServeEvent Listener (preview-safe capture)**
   - Build `TrafficEvent` including:
     - request/response metadata, timing, stubbed flag
     - `contentType`, `bodySize`
     - **preview extraction** (first N bytes) for text-like payloads only
     - `truncated` flags when bodySize > N
     - **binary detection** and binary-safe handling
   - Persist to DynamoDB `proxy-traffic` using the same preview-safe schema
   - Publish to WebSocket broadcaster for `sessionId` using the same preview-safe payload

### B) Traffic WebSocket streaming (Ktor API port)

- Endpoint: `GET /_proxy-api/sessions/{sessionId}/traffic/ws`
- Server:
  - validates session exists
  - attaches connection to per-session channel
- On each new traffic event:
  - broadcaster pushes the **preview-safe** event JSON to all connected clients for that session
- Backpressure strategy:
  - bounded queue per connection/session
  - drop oldest or drop new (choose one and document)

### C) Stub lifecycle (Ktor API port)

- `POST /_proxy-api/stubs` (with `X-Mock-Session-Id`)
  1. Validate session is ACTIVE (or at least not CLOSED; recommended: ACTIVE only)
  2. Create stub record in DynamoDB `stubs`:
     - store the **rendered mappingJson** (WireMock-ready)
     - store `status`, `priority`, `expiresAt`, `usesLeft`, `purgeAt`
  3. Load mapping into WireMock via Admin API

- Startup behavior
  - On app start, query DynamoDB for ACTIVE stubs (not expired) for sessions that are not expired
  - Load them into WireMock so runtime matches persisted state

## Traffic capture policy (preview-safe by default)

This policy is enforced **from the beginning** for both Dynamo persistence and WebSocket streaming.

- **Preview size limit (`N`)**
  - Default: `N = 16 KB`
  - Configurable; allowed range: `8–32 KB`
- **Text vs binary detection**
  - Treat payload as “text-like” only if `Content-Type` matches this allowlist:
    - `text/*`
    - `application/json`, `application/*+json`
    - `application/xml`, `application/*+xml`
    - `text/html`
  - Otherwise treat as binary.
- **Stored fields**
  - Always store: metadata + `contentType` + `bodySize`.
  - Store body previews only for text-like payloads, limited to N bytes.
  - Set `truncated=true` when bodySize > N.
  - For binary payloads: do not store preview; store only `contentType` + `bodySize` and set `isBinary=true`.
- **Future scalability**
  - Keep optional `bodyRef` fields to support full-body storage in an external blob store later (e.g., S3) without changing the event schema or breaking clients.

## DynamoDB integration

### Tables (conceptual)

- **sessions**
  - PK: `sessionId`
  - Attributes: `status`, `createdAt`, `expiresAt`, `purgeAt` (TTL attribute)

- **proxy-traffic**
  - PK: `sessionId`
  - SK: `timestamp#requestId` (or `createdAt` as sortable + unique suffix)
  - Attributes:
    - request/response metadata (method, url/path, status), duration, target service, stubbed flag
    - `requestContentType`, `responseContentType`
    - `requestBodySize`, `responseBodySize`
    - `requestBodyPreview`, `responseBodyPreview` (text-like only, ≤ N bytes)
    - `requestBodyTruncated`, `responseBodyTruncated`
    - `isBinaryRequest`, `isBinaryResponse`
    - (future) `requestBodyRef`, `responseBodyRef`
    - `purgeAt` (TTL)

  **Important:** do not store full bodies in DynamoDB items. This avoids DynamoDB’s 400 KB item size limit and reduces cost and risk.

- **stubs**
  - PK: `sessionId`
  - SK: `stubId`
  - Attributes: `status`, `priority`, `expiresAt`, `usesLeft`, `mappingJson`, `purgeAt` (TTL)

### TTL policy
- Use `purgeAt` as the DynamoDB TTL attribute across all tables.
- Derive:
  - sessions: `purgeAt = expiresAt + 48h` (per requirements)
  - traffic and stubs: typically align to session purgeAt (simplifies cleanup)

## WireMock integration

### Request filter: policy enforcement
- Must validate session ACTIVE on every proxy request.
- Trade-off: Dynamo read per request vs correctness.
  - Recommended: small in-memory cache with short TTL (1–5 seconds).

### ServeEvent listener: traffic capture
- Captures traffic from the true execution path (WireMock).
- Produces **preview-safe** traffic events:
  - previews limited to N bytes
  - binary detection and avoidance
  - stable fields supporting future full-body pointers
- Publishes to Dynamo + WS broadcaster.

### WireMock as a runtime projection
- WireMock mappings are loaded based on Dynamo’s stub state.
- Stubs created via API are stored and then projected into WireMock.

## Key design decisions (and trade-offs)

- **Keep split-plane** (WireMock proxy path remains direct)
  - ✅ simplest runtime model; matches current approach
  - ✅ avoids Ktor proxying overhead and edge-case HTTP semantics
  - ❌ requires policy enforcement inside WireMock extensions

- **Dynamo as source of truth**
  - ✅ resilience across restarts
  - ✅ enables history and multi-consumer access
  - ❌ requires projection logic into WireMock

- **Traffic persistence + streaming from listener (preview-safe contract)**
  - ✅ automatic capture with minimal duplication
  - ✅ stable WS/Dynamo payload shape from day one
  - ✅ safe defaults via truncation + binary avoidance
  - ➕ scalable upgrade path to full bodies via optional external blob-storage references (no breaking changes)

- **No background job required**
  - Expire sessions “on access” to avoid scheduling complexity.
