# Refactoring Plan — Align to strategy.md (Mock Gateway Service)

This plan restructures the existing project to match the target architecture defined in `strategy.md`. It is organized into phases, each composed of steps. Each step is intended to be implemented as a single commit. Estimates are provided per phase. At the end, you’ll find an overall effort evaluation and a recommendation on refactor vs. rewrite.

Context snapshot (repo scan):
- Monorepo with Gradle Kotlin project `app`.
- Packages under `app/src/main/kotlin/se/strawberry/{admin,api,app,common,config,maintenance,stubs,util,wiremock}`.
- WireMock and proxy-like logic likely under `wiremock` and `stubs`.
- Tests under `app/src/test/kotlin` with API, stubs, tests; reports show tests run.
- `strategy.md` defines target architecture: Ktor service with embedded WireMock 3, session isolation, PostgreSQL, WebSocket live events, Stub Manager, AWS infra.

Assumptions:
- Current project is not ktor based, so the migration should be done
- DB persistence may be minimal or absent; we’ll introduce a clean persistence layer.
- WebSocket support may be partial; we’ll move to session-scoped hub.

---

## Phase R0 — Analysis and Target Architecture Design
Goal: Document current state and plan target structure.
Estimate: 0.5–1.0 day

Steps (commits):
1. docs: add current architecture overview and pain points
   - Create `docs/current-architecture.md` summarizing modules, routing, WireMock usage, config, and test coverage.
2. docs: define target architecture and refactoring strategy
   - Create `docs/target-architecture.md` mirroring `strategy.md` specifics and mapping gaps from current state.
3. chore: add repo tasklist and labels for phased refactor
   - Add `docs/refactor-checklist.md` and GitHub labels for tracking.

## Phase R1 — Modular Restructuring (no behavior changes)
Goal: Separate domain, services, repositories, and integration clients.
Estimate: 1.5–2.5 days

Steps (commits):
1. refactor: introduce module-style packages and layer boundaries
   - Create packages: `domain.session`, `domain.traffic`, `domain.stub`; `service.*`; `repo.*`; `integration.wiremock`.
   - Move existing classes into new packages without changing behavior.
2. refactor: introduce service layer interfaces
   - Define interfaces: `SessionService`, `TrafficService`, `StubService`, `WireMockClient`.
   - Provide adapters wrapping existing implementations to keep tests green.
3. refactor: move persistence logic behind repositories
   - Define `SessionRepository`, `RecordedRequestRepository`, `StubRepository`.
   - Extract DB/file/Memory logic out of services into repos; preserve current storage behavior.
4. chore: add package-level README docs
   - `README.md` files in new packages explaining responsibility and public contracts.

## Phase R2 — Sessions (compatibility-preserving)
Goal: Introduce sessions and thread them through traffic and storage without breaking existing APIs.
Estimate: 1–2 days

Steps (commits):
1. feat: add Session entity and repository
   - `domain.session.Session` with id/name/owner/timestamps/status.
   - Implement `SessionRepository` (in-memory first; DB to follow in R6).
2. feat: add optional sessionId support to existing APIs
   - Accept `sessionId` via path `/:{sessionId}/...` or query/header `X-Mock-Session` without requiring it.
3. feat: store recorded requests with optional sessionId
   - Extend `RecordedRequest` to include `sessionId`.
   - Ensure traffic capture (current) attaches `sessionId` if present.
4. refactor: update frontend/test contracts to support session context
   - Adjust tests and any API clients to include `sessionId` where relevant.

## Phase R3 — WireMock 3 Upgrade and Stub Manager
Goal: Standardize stub lifecycle via a dedicated service and WireMock 3 embedded.
Estimate: 2–3 days

Steps (commits):
1. chore: upgrade to wiremock 3 embedded and centralize configuration
   - Pin WireMock 3 dependency; single configuration module for host/proxyBaseUrl/etc.
2. refactor: introduce WireMock client and mapping factory
   - `integration.wiremock.WireMockClient` for mapping CRUD.
   - `integration.wiremock.MappingFactory` producing JSON mappings with session-aware matchers.
3. feat: implement StubService using WireMock client
   - Create/update/disable/enable/delete stubs; sync state to DB/in-memory repository.
4. refactor: rewrite existing stub logic to use StubService
   - Replace ad hoc stub code; route all stub ops through the new service.
5. test: add integration tests for stub lifecycle and mappings
   - Ensure mappings apply correctly and persist across restarts if applicable.

## Phase R4 — Session Isolation in Proxy Layer
Goal: Ensure per-session isolation via `X-Mock-Session` header end-to-end.
Estimate: 1–1.5 days

Steps (commits):
1. feat: extract sessionId from path or headers in proxy pipeline
   - Central middleware to resolve `sessionId` and attach to request context.
2. feat: enforce X-Mock-Session header for wiremock traffic
   - Always send `X-Mock-Session: {sessionId}` to WireMock.
3. refactor: update stub creation to use session-aware mappings
   - All new mappings include header matcher for `X-Mock-Session`.
4. migration: backfill existing stubs with default/global session
   - Migrate legacy mappings to `sessionId="global"` or split per scenario.

## Phase R5 — WebSocket and Domain Events
Goal: Live traffic and stub lifecycle events per session.
Estimate: 1.5–2 days

Steps (commits):
1. refactor: introduce WebSocket hub with per-session channels
   - Implement hub managing connections keyed by `sessionId`.
2. feat: publish traffic events from TrafficService
   - On record, emit `TRAFFIC_EVENT` to hub.
3. feat: publish stub lifecycle events to WebSocket
   - Emit `STUB_CREATED/UPDATED/DELETED` on stub ops.
4. test: add WS integration tests for session-scoped events
   - Verify correct routing and fan-out to connected clients.

## Phase R6 — Database Integration (PostgreSQL)
Goal: Persist sessions, recorded_requests, and stubs.
Estimate: 2–3 days

Steps (commits):
1. chore: add Postgres dependency and configuration
   - Add driver; config via env/Secrets; local docker-compose for dev.
2. feat: add migrations for sessions and recorded_requests (Flyway/Liquibase)
   - Initial schema; apply automatically on startup.
3. feat: implement repositories with DB persistence
   - Replace in-memory adapters with DB-backed implementations.
4. feat: add stubs table and migrations
   - Store stub metadata and `wiremockMappingId`.
5. test: add persistence integration tests
   - CRUD and referential checks, session linkage.

## Phase R7 — Traffic Recorder (WireMock PostServe)
Goal: Use WireMock PostServeAction to capture and persist traffic.
Estimate: 1–1.5 days

Steps (commits):
1. feat: implement WireMock PostServe extension
   - Extension capturing request/response and forwarding to `TrafficService`.
2. feat: implement TrafficService persistence and API
   - Save to DB; expose `GET /api/sessions/{sessionId}/traffic`.
3. test: end-to-end traffic recording flow
   - Request -> WireMock -> Recorder -> DB -> read via API.

## Phase R8 — Non-functional Enhancements
Goal: Logging, correlation, auth.
Estimate: 1–1.5 days

Steps (commits):
1. feat: add structured logging and correlation IDs
   - JSON logs; request IDs threaded through services.
2. feat: add basic auth/token-based protection for admin APIs
   - Protect stub/session admin endpoints.
3. chore: add performance/load tests for proxy and WebSocket
   - Baseline throughput/latency; WS connection scale.

## Phase R9 — AWS Deployment Alignment (Optional)
Goal: Align deployment with AWS infra described.
Estimate: 3–5 days (infra-heavy)

Steps (commits):
1. chore: optimize Docker image (multi-stage)
2. infra: define VPC, RDS, S3 (optional) via Terraform/CDK
3. infra: define ECS Fargate + ALB (HTTP + WS)
4. chore: integrate Secrets Manager/SSM for configuration
5. chore: configure CloudWatch logs and basic alarms

---

## Cross-cutting Quality Gates (applied at each phase)
- Build and tests green (Gradle build, unit/integration tests).
- Lint/type-check for Kotlin.
- Backward-compatible API changes until explicit deprecation.
- Documentation for public contracts.

## Effort Estimation Summary
- R0: 0.5–1.0 day
- R1: 1.5–2.5 days
- R2: 1–2 days
- R3: 2–3 days
- R4: 1–1.5 days
- R5: 1.5–2 days
- R6: 2–3 days
- R7: 1–1.5 days
- R8: 1–1.5 days
- R9: 3–5 days (optional)
Total (core R0–R8): ~12–16 working days. With AWS (R9): +3–5 days.

## Refactor vs. Rewrite — Recommendation
- Current repo already has Ktor structure, WireMock integration folders (`wiremock`, `stubs`), and tests. This provides a functional baseline and reduces risk.
- The refactor emphasizes modularization and layering without immediate behavior changes, enabling incremental delivery with tests staying green.
- A full rewrite would likely take 8–12 days for core features plus 3–5 days for polish and infra, with higher risk of regressions and loss of existing knowledge/tests.

Conclusion: Refactoring is worth it.
- It leverages existing code and tests.
- The plan allows phased migration with clear rollbacks.
- Only consider a rewrite if major hidden coupling or low code quality makes R1 infeasible. Based on the current structure (multiple coherent packages and test artifacts present), refactoring is the safer and faster path to the target architecture.

