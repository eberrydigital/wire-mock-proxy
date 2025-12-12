# Refactor Checklist — Variant B (Phased, commit-sized steps)

This is the actionable plan to bring the current codebase to the target architecture. Each step is sized to be one commit.

## R0 — Analysis & Design (docs only)
- R0.1 docs: add current architecture overview and pain points (docs/current-architecture.md) Done
- R0.2 docs: define target architecture and refactoring strategy (docs/target-architecture.md) Done
- R0.3 docs: add this refactor checklist (docs/refactor-checklist.md) Done

## R1 — Modular restructuring (no behavior change)
- R1.1 refactor: introduce packages domain.session, domain.traffic, domain.stub (move types) Done
- R1.2 refactor: add service interfaces SessionService, TrafficService, StubService, WireMockClient Done
- R1.3 refactor: add repositories SessionRepository, RecordedRequestRepository, StubRepository (interfaces) Done
- R1.4 refactor: adapt existing implementation behind the new interfaces (keep public API stable) Done
- R1.5 test: smoke tests still pass (no regression) Done
- R1.6 Install necessary dependencies and prepare for migration to using Ktor in the future. Refactor this checklist to outline when Ktor refactoring is done. Pending
  - Note: Prepare a Ktor migration plan and dependency list (ktor-server-core, ktor-server-netty, ktor-websockets, ktor-client, logging). Align endpoints and routing strategy.

## R2 — Sessions (compat) 
- R2.1 feat: add Session entity, migration, and repository implementation
- R2.2 feat: extract sessionId from path /{sessionId}/… or header X-Mock-Session (proxy layer)
- R2.3 feat: enforce/propagate X-Mock-Session to WireMock for matching
- R2.4 test: integration tests for session extraction and header propagation

## R3 — Embedded WireMock consolidation & proxy routing
- R3.1 feat: centralize embedded WireMock configuration (base proxy URL, mappings dir)
- R3.2 feat: wildcard routing in server to forward non-/api paths to WireMock
- R3.3 test: end-to-end proxy flow to fake upstream

## R4 — Traffic recording to DB
- R4.1 feat: implement WireMock PostServe extension to capture request/response
- R4.2 feat: implement TrafficRecorder service persisting RecordedRequest
- R4.3 feat: GET /api/sessions/{sessionId}/traffic
- R4.4 test: e2e recording flow

## R5 — Stub management API
- R5.1 feat: POST /api/sessions/{sessionId}/stubs (from recorded request)
- R5.2 feat: GET /api/sessions/{sessionId}/stubs, GET /api/stubs/{id}
- R5.3 feat: PUT /api/stubs/{id}, POST /api/stubs/{id}/enable|disable (sync with WireMock)
- R5.4 test: stub lifecycle and WireMock mappings

## R6 — WebSocket live events
- R6.1 feat: /ws?sessionId=… endpoint
- R6.2 feat: session hub for subscriptions
- R6.3 feat: publish TRAFFIC_EVENT on record
- R6.4 feat: publish STUB_* events on changes

## R7 — Cloud deployment (AWS)
- R7.1 chore: Dockerfile (multi-stage), image publish
- R7.2 infra: VPC, RDS, ECS+ALB (IaC placeholder)
- R7.3 chore: Secrets Manager/SSM integration for config
- R7.4 chore: CloudWatch logs & basic metrics

## R8 — NFRs
- R8.1 feat: structured JSON logging + correlation IDs
- R8.2 feat: admin/API auth
- R8.3 chore: performance/load tests

---

## Effort Estimate & Recommendation

Assumptions:
- Existing code uses embedded WireMock with custom proxy controls and some test coverage.
- Ktor or a similar server framework is present (if not, adding it is moderate effort).

High-level effort (developer-days):
- R0: 0.5d (done)
- R1: 2–3d (code moves, interfaces, no behavior change)
- R2: 2–3d (session entity, migrations, header/path handling, tests)
- R3: 1–2d (wiremock consolidation, routing)
- R4: 3–4d (post-serve extension, DB persistence, API, tests)
- R5: 3–4d (stub APIs, wiremock sync, tests)
- R6: 2–3d (WebSocket endpoint/hub/events)
- R7: 4–6d (Docker, CI, AWS infra integration)
- R8: 2–3d (logging, auth, perf tests)

Total: ~21–30 developer-days for a single engineer, or ~4–6 calendar weeks with typical context switching and reviews.

Rewrite vs Refactor:
- Given the current project already embeds WireMock, has resource layout, tests, and a working proxy concept, refactoring is preferable. We can incrementally introduce sessions, DB, and APIs without halting existing value.
- A rewrite would only be faster if the server framework is absent or the code is tightly coupled and untested. The repo shows tests and structure, so incremental refactor offers lower risk and faster delivery of features.

Conclusion: Proceed with refactoring. Start with R1 to establish clean boundaries, then add sessions and persistence.
