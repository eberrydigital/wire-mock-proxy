# Target Architecture — Mock Gateway Service (from strategy.md)

This document captures the desired end‑state to guide refactoring. It’s tailored from `strategy.md` and maps gaps from the current implementation.

## Components

- Mock Gateway Service (Ktor + embedded WireMock 3)
  - REST API: sessions, stubs, traffic viewing/export
  - WebSocket: live traffic/events per session
  - Embedded WireMock:
    - Proxies to Service B (configurable)
    - Applies stubs
- PostgreSQL (AWS RDS)
  - Tables: `sessions`, `recorded_requests`, `stubs` (+ optional `projects`, `users`)
- Frontend (separate service that consumes API)
- AWS infra
  - VPC/private subnets, ECS Fargate + ALB (WebSocket), RDS, S3 (optional), CloudWatch, Secrets Manager/SSM

## Request flow

1. Client → `https://mock-gateway.company.com/{sessionId}/...` or via header `X-Mock-Session-Id`
2. Mock Gateway extracts/guarantees session header
3. Request goes into embedded WireMock
4. WireMock matches stubs scoped by session header; otherwise proxies to Service B
5. PostServe hook records request/response to DB and publishes WebSocket events

## Domain model

- Session: id, meta, timestamps, status
- RecordedRequest: id, sessionId, request/response fields, timestamp
- Stub: id, sessionId, name/desc, requestMatcher, responseSpec, wiremockMappingId, enabled, timestamps

## Non‑functional requirements

- Structured JSON logging, correlation IDs
- Admin/API auth
- Performance/load tests for proxy and WebSocket

## Refactoring path (Variant B)

- R0: Analysis & design docs
- R1: Modular restructuring (domain, services, repos, WireMock client) without behavior changes
- R2: Introduce sessions compatibly
- R3: Embedded WireMock consolidation & proxy routing
- R4: Traffic recording to DB via WireMock PostServe extension
- R5: Stubs management API with session scoping and WireMock sync
- R6: WebSocket live events
- R7: Cloud deployment (Docker, ECS/ALB, RDS, Secrets, logs)
- R8: NFRs (logging, auth, perf)

## Gaps vs current state

- DB persistence missing → add Postgres + migrations + repos
- Sessions optional → enforce and propagate via headers/path
- WebSocket not present → add endpoint + hub + event publishing
- Clear modular boundaries missing → introduce domain/services/repos layers
- Cloud deploy scaffolding unclear → add Dockerfile, CI, infra manifests
- Security and observability need upgrades → auth, structured logs, metrics

