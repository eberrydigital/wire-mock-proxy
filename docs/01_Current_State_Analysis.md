# Current State Analysis

This document describes the current implementation found in `app.zip` and highlights strengths, limitations, technical debt, and deviations from `Requirements.md`.

## Project structure

```
app/
  build.gradle.kts

  src/main/kotlin/se/strawberry/
    app/
      Main.kt
      AppDependencies.kt
      KtorBootstrap.kt
      ServerBootstrap.kt
    api/
      Endpoints.kt
      KtorApp.kt
      KtorExtensions.kt
      models/
        errors/*
        health/*
        sessions/*
        stubs/*
        traffic/*
    common/
      Json.kt
      Names.kt
    config/
      AppConfig.kt
      AppConfigLoader.kt
      DynamoConfig.kt
      Env*.kt
    helpers/
      SessionHelper.kt
    infrastructure/dynamo/
      DynamoBootstrap.kt
      DynamoClientFactory.kt
    repository/
      session/*      (interface + Dynamo implementation)
      traffic/*      (interface only)
      stub/*         (interface only)
    service/
      request/*      (WireMock serve-events read)
      stub/*         (WireMock stub CRUD)
      wiremock/*     (client wrapper)
    wiremock/
      StubBuilder.kt
      filters/DynamicRoutingGuard.kt
      listeners/EphemeralServeEventListener.kt
      matchers/TtlGuardMatcher.kt
      templating/ServiceTemplateHelpers.kt

  src/main/resources/wiremock/__files/ui/*

  src/test/kotlin/** (unit + integration tests)
  src/test/resources/.env.test, logback-test.xml
```

## Key components and responsibilities

### Runtime / bootstrap
- **`Main.kt`**
  - Loads configuration
  - Builds DynamoDB client
  - Ensures sessions table exists
  - Starts WireMock + Ktor

- **`ServerBootstrap.kt`**
  - Starts embedded WireMock
  - Installs WireMock extensions
  - Registers a fallback proxy mapping that proxies requests to the upstream service resolved from `X-Mock-Target-Service`

- **`KtorBootstrap.kt`**
  - Starts Ktor on `API_PORT` and registers routes

### Ktor management API
- **Health**
  - `GET /_proxy-api/health`

- **Sessions**
  - `POST /_proxy-api/sessions`
  - `GET /_proxy-api/sessions/{sessionId}`
  - `POST /_proxy-api/sessions/{sessionId}/close`

- **Traffic**
  - `GET /_proxy-api/traffic`
  - `GET /_proxy-api/traffic/{requestId}`
  - `DELETE /_proxy-api/traffic`
  - `GET /_proxy-api/traffic/export`

  **Implementation note:** traffic is sourced from WireMock serve-events (in-memory).

- **Stubs**
  - `POST /_proxy-api/stubs`
  - `GET /_proxy-api/stubs`
  - `DELETE /_proxy-api/stubs/{stubId}`

  **Implementation note:** stubs are stored in WireMock mappings (in-memory).

### WireMock integration
- **`DynamicRoutingGuard` (request filter)**
  - Validates required headers exist
  - Validates `X-Mock-Target-Service` exists in service map
  - Validates upstream port is in an allowed list
  - Does *not* validate session existence/ACTIVE status

- **Ephemeral stub behavior**
  - `EphemeralServeEventListener` + `TtlGuardMatcher`
  - Implements TTL and “uses left” guard based on mapping metadata
  - Operates in WireMock memory (not persisted)

- **`StubBuilder`**
  - Converts API stub DTOs into WireMock mapping JSON

### Persistence
- DynamoDB is currently used for **sessions only**
  - Traffic repository is interface only
  - Stub repository is interface only

## Existing architectural patterns

- **Split-plane runtime**
  - WireMock handles the actual proxy request path on its own port
  - Ktor exposes a management API on a separate port

- **Partial layering**
  - There are repositories and services, but boundaries are not strict:
    - some Ktor routes call services, others call repositories
    - WireMock is used both as execution engine and as state store (traffic + stubs)

- **Manual DI**
  - Dependencies are built manually through `buildDependencies()`

## Strengths

- Reasonable package separation (config / API / wiremock extensions / persistence).
- Embedded WireMock with templating is a good fit for dynamic routing.
- Ephemeral stub semantics (TTL/uses) already exist and can be reused.
- There is a test base (unit/integration/LocalStack) enabling safer refactors.

## Limitations & technical debt

1. **Proxy path does not enforce session ACTIVE**
   - The request filter checks headers but does not consult the session repository.
2. **Traffic is not persisted**
   - Serve-events are volatile and disappear on restart; cannot support “history”.
3. **No WebSocket streaming**
   - Requirements call for real-time streaming; current system has none.
4. **Stubs are not persisted**
   - Stubs do not survive restart and are not session-scoped at the persistence layer.
5. **Session lifecycle incomplete**
   - No EXPIRED handling, no read-only semantics after expiry, no purgeAt TTL usage.
6. **Dynamo bootstrap incomplete**
   - Only sessions table is ensured; no traffic/stubs tables or TTL enabling.
7. **API scoping gaps**
   - `GET /stubs` returns all mappings, not session-scoped.
8. **Packaging hygiene issue**
   - A Dynamo session repository file appears to be in the default package, which is fragile.
9. **Global mutable state**
   - `ServerRef.server` is global; reduces testability and complicates future extension.

## Deviations from Requirements.md (high impact)

- Missing DynamoDB persistence for `proxy-traffic` and `stubs`.
- Missing WebSocket streaming endpoint and pipeline.
- Missing “ACTIVE session required for proxy traffic acceptance” enforcement.
- Missing required session lifecycle behaviors (expire, read-only, TTL purge).
