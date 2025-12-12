# Current Architecture — Wire Mock Proxy (as of 2025-12-12)

This document summarizes what exists today in the repository: modules, runtime flow, dependencies, data, and tests. It’s based on the repo layout and README.

- Language/Runtime: Kotlin/JVM
- Build: Gradle (Kotlin DSL)
- App module: `app/` (WireMock is embedded; Ktor scaffold exists but not yet in use)
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
- Session scoping via `X-Mock-Session-Id` (mandatory by refactor; previously optional)
- Ephemeral stubs supported with TTL and limited usages
- Safety rails: request filtering, health bypasses, masking secrets in the API

Startup example:

- Env: `DYN_ALLOWED_PORTS`, `SERVICE_MAP`, `PORT`
- Command: `./gradlew run`

## Request flow (current)

1. Client sends request with `X-Mock-Target-Service: {serviceName}` and `X-Mock-Session-Id: {sessionId}`
2. WireMock checks mapping; if a matching stub exists, it returns stubbed response
3. If not stubbed, proxy routes to mapped upstream (from `SERVICE_MAP`) and returns real response

## Data and persistence

- No DB is currently referenced in README; traffic and stubs are managed by WireMock resources and in-memory ephemeral constructs
- Mappings and `__files` exist in resources; ephemeral stubs likely stored in memory or temporary mapping files during runtime

## API

- API for request inspection, export traffic, and create/delete stubs
- Public API is planned under `/_proxy-api/*` (Ktor migration in progress); currently handled via WireMock transformer

## Tests and coverage

- Unit/integration tests present in `app/src/test/kotlin`
- Generated test reports exist under `app/build/reports/tests/test/index.html`
- Test suites mention ephemeral stubbing behavior and session-scoped stubbing

## Pain points and gaps (observed/inferred)

- Session handling was optional; we’ve enforced header-only to avoid ambiguity
- No explicit database for recorded traffic/stubs; limits persistence, querying, and multi-user scenarios
- Architecture appears monolithic; domains (session, traffic, stubs) are not clearly separated
- WebSocket/live traffic not explicitly present; likely missing for realtime consumers
- Cloud deployment and secret management not yet formalized
- Target routing relies solely on a header; needs validation and security controls

## Dependencies (high-level)

- Kotlin, Gradle
- WireMock (embedded)
- Ktor (scaffold added; migration planned)

## Glossary

- Stub: WireMock mapping returning a predefined response
- Ephemeral stub: Mapping with limited TTL/uses
- Session: Identifier used to scope stubs and traffic via header `X-Mock-Session-Id`
- SERVICE_MAP: Env-provided name→origin mapping for upstream proxies
