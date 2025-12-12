# Current Architecture — Wire Mock Proxy (as of 2025-12-12)

This document summarizes what exists today in the repository: modules, runtime flow, dependencies, data, and tests. It’s based on the repo layout and README.

- Language/Runtime: Kotlin/JVM
- Build: Gradle (Kotlin DSL)
- App module: `app/` (Ktor not explicitly confirmed in README, but WireMock is embedded)
- WireMock: Embedded WireMockServer with mappings and __files present under resources
- Tests: Kotlin tests under `app/src/test/kotlin` with WireMock fixtures; Gradle test reports available

## Modules and structure

- Root Gradle project
- Single application module: `app/`
  - Sources: `app/src/main/kotlin/se/strawberry/...`
  - Resources:
    - `app/src/main/resources/wiremock/mappings/` — WireMock mappings
    - `app/src/main/resources/wiremock/__files/` — WireMock response bodies
  - Test resources mirror: `app/src/test/resources/wiremock/...`

## Runtime behavior (from README)

- The server embeds a WireMock server and proxies traffic based on header `X-Mock-Target-Service`
- Upstream targets are defined by env var `SERVICE_MAP` (e.g., `omni=https://api.test.eberry.digital`)
- Unknown or disallowed target service names return 400
- Optional scoping via `X-Mock-Session-Id` (README notes it should become mandatory)
- UI exists under `/_proxy-ui` with a copied sessionId used by clients for scoping
- Ephemeral stubs supported with TTL and limited usages
- Safety rails: request filtering, UI/health bypasses, masking secrets in UI/API

Startup example:

- Env: `DYN_ALLOWED_PORTS`, `SERVICE_MAP`, `PORT`
- Command: `./gradlew run`

## Request flow (current)

1. Client sends request with `X-Mock-Target-Service: {serviceName}`
2. WireMock checks mapping; if a matching stub exists, it returns stubbed response
3. If not stubbed, proxy routes to mapped upstream (from `SERVICE_MAP`) and returns real response
4. Optional: `X-Mock-Session-Id` used to scope stubs

## Data and persistence

- No DB is currently referenced in README; traffic and stubs are managed by WireMock resources and in-memory ephemeral constructs
- Mappings and `__files` exist in resources; ephemeral stubs likely stored in memory or temporary mapping files during runtime

## API & UI

- API for request inspection, traffic export, and stub management (details inferred, not enumerated in README)
- Minimal UI served from `/_proxy-ui`

## Tests and coverage

- Unit/integration tests present in `app/src/test/kotlin`
- Generated test reports exist under `app/build/reports/tests/test/index.html`
- Test suites mention ephemeral stubbing behavior and session-scoped stubbing

## Pain points and gaps (observed/inferred)

- Session handling is optional; should be enforced for isolation
- No explicit database for recorded traffic/stubs; limits persistence, querying, and multi-user scenarios
- Architecture appears monolithic; domains (session, traffic, stubs) are not clearly separated
- WebSocket/live traffic not explicitly present; likely missing for realtime UI
- Cloud deployment and secret management not yet formalized
- Target routing relies solely on a header; needs validation and security controls

## Dependencies (high-level)

- Kotlin, Gradle
- WireMock (embedded)
- Possibly Ktor or another server framework (not explicitly listed here)

## Glossary

- Stub: WireMock mapping returning a predefined response
- Ephemeral stub: Mapping with limited TTL/uses
- Session: Identifier intended to scope stubs and traffic
- SERVICE_MAP: Env-provided name→origin mapping for upstream proxies

