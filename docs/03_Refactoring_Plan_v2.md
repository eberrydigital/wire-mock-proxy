# Refactoring Plan (One-commit, independently releasable steps)

This checklist is organized into phases. Each step should be:
- one commit
- independently releasable
- minimal risk
- preserving existing behavior unless explicitly stated

## Phase 0 — Baseline hygiene & scaffolding

1. **Commit: Fix repository packaging / naming** :Done
   - Goal: remove default-package repository risk and standardize imports.
   - Outcome: session repository implementation lives in a proper package; build remains green.

2. **Commit: Add domain models and policy helpers (no behavior change)**
   - Goal: centralize session/stub lifecycle rules.
   - Outcome: `domain` module/package exists; API and runtime unchanged.

3. **Commit: Define preview-safe TrafficEvent contract in domain**
   - Goal: lock in “metadata + previews (≤ N) + bodySize + truncated + binary detection” from the beginning.
   - Outcome: `TrafficEvent` includes preview and binary fields; no runtime behavior change yet.

4. **Commit: Introduce `SessionPolicy` service and use it in Ktor routes**
   - Goal: unify validation behavior across endpoints.
   - Outcome: routes call `SessionPolicy` for “exists / active / expired” decisions; responses unchanged.

## Phase 1 — Session lifecycle compliance

5. **Commit: Add `purgeAt` calculation on session creation**
   - Goal: align with session TTL strategy (expiresAt + 48h).
   - Outcome: created sessions contain both `expiresAt` and `purgeAt`.

6. **Commit: Extend Dynamo session schema to persist `purgeAt` and EXPIRED state**
   - Goal: persist lifecycle fields safely.
   - Outcome: reads/writes support new attributes; backward compatible.

7. **Commit: Implement expire-on-access**
   - Goal: if `now > expiresAt`, mark session EXPIRED when loaded/used.
   - Outcome: no cron needed; session rule enforcement becomes consistent.

## Phase 2 — Traffic persistence foundation (preview-safe from day one)

8. **Commit: Add preview extraction utility + content-type classifier**
   - Goal: implement reusable logic:
     - allowlist text-like content-types
     - detect binary vs text
     - extract first N bytes as preview
     - compute `truncated` flag
   - Outcome: pure utility with unit tests; not wired to runtime yet.

9. **Commit: Add Dynamo traffic table bootstrap + repository (preview-safe schema)**
   - Goal: create `proxy-traffic` schema storing:
     - metadata
     - `contentType`, `bodySize`
     - previews (≤ N) for text only
     - binary flags + truncated flags
     - TTL (`purgeAt`)
   - Outcome: table exists; no runtime usage yet.

10. **Commit: Persist preview-safe traffic from WireMock serve-event listener**
    - Goal: every proxied/stubbed request is recorded using the preview/binary policy.
    - Outcome: traffic survives restart; captured items are safe by default.

11. **Commit: Switch Ktor traffic endpoints to read from Dynamo**
    - Goal: REST endpoints become consistent with persisted preview-safe data.
    - Outcome: list/get/export/clear operate on Dynamo traffic.

## Phase 3 — WebSocket streaming (preview-safe payload)

12. **Commit: Add WebSocket endpoint + broadcaster skeleton**
    - Goal: stable WS URL and connection handling.
    - Outcome: frontend can connect; no events yet.

13. **Commit: Publish preview-safe traffic events to broadcaster from WireMock listener**
    - Goal: stream the same preview-safe event contract used for persistence.
    - Outcome: clients receive events live; no full bodies are pushed.

14. **Commit: Add backpressure + bounded queues**
    - Goal: prevent memory blowups.
    - Outcome: documented drop policy; stable behavior under load.

## Phase 4 — Stub persistence + projection

15. **Commit: Add Dynamo stubs table bootstrap + repository**
    - Goal: persist stub definitions and lifecycle.
    - Outcome: storage exists; runtime unchanged.

16. **Commit: Make `POST /stubs` write to Dynamo and project to WireMock**
    - Goal: Dynamo becomes source of truth for newly created stubs.
    - Outcome: stubs survive restart and are active immediately.

17. **Commit: Scope `GET /stubs` to session and enforce session rules**
    - Goal: match “session-scoped stubs” requirement.
    - Outcome: correct scoping; reduced accidental cross-session visibility.

18. **Commit: Load active stubs from Dynamo into WireMock on startup**
    - Goal: restore runtime state after restart.
    - Outcome: WireMock mappings reflect persisted stubs.

## Phase 5 — Proxy-path enforcement (critical compliance)

19. **Commit: Enforce ACTIVE session in WireMock request filter**
    - Goal: reject proxy traffic when session is CLOSED/EXPIRED/non-existent.
    - Outcome: requirement satisfied on the real proxy path.

20. **Commit: Add short-lived session cache in filter**
    - Goal: reduce Dynamo reads per request while keeping correctness.
    - Outcome: lower latency; same externally observable behavior.

## Phase 6 — Cleanup & hardening

21. **Commit: Remove legacy WireMock serve-events dependency from Ktor request services**
    - Goal: simplify; Dynamo is the only traffic source for API.
    - Outcome: fewer moving parts; better restart behavior.

22. **Commit: Replace global `ServerRef` with injected server/client**
    - Goal: improve testability and reduce implicit state.
    - Outcome: clearer bootstrapping; easier future extension.

23. **Commit: Add contract tests for lifecycle rules**
    - Goal: lock in requirements.
    - Outcome: regression safety for sessions, stubs (ttl/uses), traffic persistence (preview/binary rules), and WS streaming.
