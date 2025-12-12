# Ktor Migration Plan — Public API and Runtime Cutover (Backend-first)

Goal: Move public API handling from WireMock extensions to a Ktor application that embeds WireMock 3, keeping existing endpoints stable and not serving the UI from this repo.

## Guiding principles
- Backend-first: this repo exposes APIs and reverse proxy only. UI is a separate consumer deployed elsewhere.
- Route parity: keep `/_proxy-api/*` identical; proxy all other non-admin paths to embedded WireMock.
- Direct cutover: no runtime toggle; cut over on a short-lived branch after parity tests.

## Endpoints and responsibilities
- Ktor:
  - REST API under `/_proxy-api/*` — Sessions, Stubs, Traffic.
  - WebSocket under `/ws` — live traffic/events per session (R6).
  - Wildcard reverse proxy: forward any non-internal path to the embedded WireMock port.
- WireMock (embedded):
  - Matching stubs and proxying upstream responses.
  - Post-serve extension for traffic capture (R4).

## Phased migration
1) K0 — Dependencies & skeleton
- Add Ktor deps (done)
- Create Ktor `Application` with `/health` and route placeholders

2) K1 — Implement public API in Ktor
- Implement `/_proxy-api/stubs` (create/list/delete) using StubService
- Implement `/_proxy-api/requests` (list/byId/clear/export) using TrafficService
- Implement `/_proxy-api/sessions` (create/get/close) using SessionService

3) K2 — Reverse proxy to WireMock
- Start embedded WireMock on an internal port
- Ktor forwards all non-internal paths to WireMock via Ktor client

4) K3 — Cutover
- Replace current ServerBootstrap startup to initialize Ktor first, then embedded WireMock
- Remove RequestsApiTransformer from WireMock

5) K4 — Post-cutover cleanup
- Keep matchers/listeners & post-serve hooks
- Harden logging/auth (R8)

## Testing strategy
- Parity tests for `/_proxy-api/*` operations (list/create/delete stubs, list/byId/clear/export requests)
- Golden file tests for representative JSON outputs
- E2E proxy tests: Ktor → WireMock internal → upstream

## Config
- Ports:
  - Ktor: external port (`PORT`)
  - WireMock: internal ephemeral port (0)

## Risks and mitigations
- Response drift — Golden tests and parity checks
- Performance — Keep reverse proxy path minimal; reuse OkHttp/Ktor client with pooling
- Coupling — Services own business logic; Ktor remains thin
