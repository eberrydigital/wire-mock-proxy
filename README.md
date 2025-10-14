# Wire Mock Proxy — Architecture & Contributor Guide

> A proxy‑driven mocking server built on **WireMock** for testers and web developers. It captures real traffic, lets you mint stubs from requests, and can patch or fully stub responses — all without leaving your browser.

---

## 1) What this project is

**wire-mock-proxy** is a Kotlin/JVM application that embeds a `WireMockServer` and adds:

* **Dynamic upstream routing** via a required header `X-Mock-Target-Service` mapped to an origin URL (SERVICE_MAP).
* **API & minimal UI** to inspect requests, export traffic, and create/delete stubs.
* **Ephemeral stubs** with TTL and limited use counters.
* **Safety rails** (request filter that blocks bad/unknown routes, UI asset/health paths bypass, masking secrets in the UI/API).

It’s designed to be dropped into test/staging environments where you want to:

* Proxy traffic to real services, while capturing requests.
* Promote captured requests to **stubs** (scoped to a session if needed).
* Override live responses incrementally (patch) or completely (static body).

---

## 2) High‑level architecture

```
1. Client sends request:
Client --> [WireMock checks if there's target service and if it's in env.SERVICE_MAP] -> Upstream Service 
2. Upstream Service sends response:
Upstream Service --> [Instead of actual response WM sends stub if there's a matching stub] -> Client
```

## 3) How it works

```
1. Server is started with the map of services that it can do mapping to for example: SERVICE_MAP=omni=https://api.test.eberry.digital
2. Client sends request with the header X-Mock-Target-Service: serviceName. Service names are somethign client and service should agree on.
3. If serviceName matches one of the keys in SERVICE_MAP, the request is proxied to the corresponding URL. Otherwise we send back 400.
4. Client also sends the header X-Mock-Session-Id. This is optional (:TODO make it mandatory) and is used to scope stubs to a session.
5. Currently client doesn't know session id, so we should manually go to ui host:port/_proxy-ui and copy sessionId. Then modify headers via for example simple-modify-headers chrome extension with the value of sessionId.
```
### Start

```bash
# From repo root
DYN_ALLOWED_PORTS=80,443 SERVICE_MAP=omni=https://api.test.eberry.digital PORT=8080 ./gradlew run
```


