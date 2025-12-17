# Requirements for a proxy tool

The idea of this tool is to provide user with the ability to:
- create proxy sessions that will forward traffic to target services while capturing and displaying this traffic in real time. 
- user can also define stubs within a session to mock specific endpoints.
- based on recorded traffic, user can create stubs to mock specific endpoints.
- we believe that it will be useful for testing and debugging purposes, allowing users to inspect and manipulate traffic between clients and services.

## Core Concepts
### Session

A session represents an isolated proxying context:
- All traffic is associated with exactly one session
- Stubs are scoped to a session
- Sessions have a limited lifetime
- Sessions can be ACTIVE, CLOSED, or EXPIRED

### Traffic

Traffic is: 
- Captured automatically
- Streamed in real time
- Persisted for later inspection

The way we work with traffic is descrbied in the file docs/02_Target_Architecture_v2.md in the section.

### Stub
A stub defines how specific requests should be intercepted and mocked:

- Scoped to a session
- Matches requests based on method, path, headers, body, etc.
- Returns a predefined response
- May be ephemeral (TTL and/or limited uses)
- Has priority
- Is persisted and survives restarts

## Persistence Requirements
### DynamoDB Tables

#### sessions

- PK: sessionId
- Attributes:
  - name
  - owner
  - status (ACTIVE, CLOSED, EXPIRED)
  - createdAt
  - expiresAt
  - purgeAt (TTL, createdAt + 48h)

#### proxy-traffic

- PK: sessionId
- SK: time-based sort key
- Attributes:
  - request data (method, path, headers, body)
  - response data (status, headers, body)
  - duration
  - target service
  - stubbed flag
  - purgeAt (TTL)

#### stubs

- PK: sessionId
- SK: stubId
- Attributes:
  - status (ACTIVE, EXPIRED, EXHAUSTED)
  - priority
  - ttl / expiresAt
  - usesLeft
  - mappingJson (WireMock mapping)
  - createdAt
  - updatedAt
  - purgeAt (TTL)


## Session Lifecycle Rules

- Default session TTL: 24 hours
- After expiration:
- Session becomes EXPIRED
- No new proxy traffic is accepted
- Traffic and stubs are read-only
- After 48 hours total:
- All session-related data is removed via DynamoDB TTL

## Proxying Rules

Client requests must include headers:

- X-Mock-Target-Service

- X-Mock-Session-Id

The proxy must:

- Validate that the target service exists in SERVICE_MAP

- Validate that the session exists and is ACTIVE

- Forward the request to the correct upstream service

- Capture request and response

- Publish traffic events in real time

## Service Map

Provided via environment variable SERVICE_MAP

Format:
```payment-api=https://payment.prod.company.com,user-api=https://users.prod.company.com```
Proxy uses this map to route requests dynamically


## Technical Requirements
Key points:
- API is implemented as KTOR
- DB persistence for storing and managing sessions, traffic, and stubs (stubs will implemented almost in the last stage, before that in-memory stubs can be used for development and testing)
- WebSocket support is not implemented, but it is necessary so that Frontend could show the proxying traffic within a session in real time.
- WireMock 3 is used as an embedded server for stubbing and proxying.
- KTOR is responsible for routing, session management, WireMock integrations, and business logic.
- Persistent stubs PR: sessionId, SK: stubId. Attributes: status (ACTIVE/EXPIRED/EXHAUSTED), createdAt, updatedAt, expiresAt, usesLeft (nullable), priority, mappingJson (WireMock mapping JSON as the “rendered” truth), purgeAt (epoch seconds) = session.createdAt + 48h (or stub created + 48h). Storing mappingJson is pragmatic: you don’t have to perfectly re-create WireMock mappings from a custom model later. 
- The project is under development, so backward compatibility is not a concern at this point.
- It is agreed that user will provide service map which consists of the name of the service and its target URL. The proxy will use this map to forward traffic to the correct target service There can be multiple services defined in the map.
- TTL for sessions should be 24 hours. After that, the sessions should be expired. The related data should be available for the next 48 hours.
- The future frontend application will be developed separately. The backend should provide the necessary APIs and WebSocket support to enable real-time traffic monitoring and stub management from the frontend.

## Requirements for writing code:
- Follow SOLID principles and clean architecture.
- Write unit and integration tests for all new features.
- Strings should be stored in constants.
- Use dependency injection where appropriate.
- Ensure proper error handling and logging.
- Follow the existing coding style and conventions used in the project.


## User stories
1. As a user, I want to create a new proxy session so that I can start capturing traffic.
2. As a user, I can point my client to the proxy server so that all my requests are routed through the proxy to the target services that I defined in the service map.
3. As a user, I want to see the captured traffic in real time within the session so that I can monitor the requests and responses.
4. As a user, I want to create stubs within a session so that I can mock specific endpoints.
5. As a user, I want to create stubs via endpoints /_proxy-api/stubs"
6. As a user, I want to create stubs based on recorded traffic via the user interface

## AI Agent Instructions
- When implementing the requirements, ensure that the code adheres to the specified technical requirements and coding standards.
- Focus on modularity and separation of concerns to facilitate future maintenance and enhancements.
- Before creation DOCUMENTATION you MUST ask the user first if they want it. 

## User flow described as text

### Initial Setup (One-time, typically by DevOps/Infrastructure)
The Wire Mock Proxy service is deployed and configured with:
- Available target services mapped in the SERVICE_MAP environment variable (e.g., `payment-api=https://payment.prod.company.com,user-api=https://users.prod.company.com`)
- The backend service running and accessible
- The frontend application deployed and connected to the backend

### Expected number of users
- 20 users per month

### Expected number of sessions
- 100 sessions per month

### Expected number of stubs
- up to 10000 stubs per month

---

### User Journey: From Login to Testing

#### 1. User Opens the Frontend Application
- User navigates to the Wire Mock Proxy web UI (e.g., `https://proxy-tool.company.com`)
- Sees the main dashboard with options to:
  - Create a new session
  - Browse available target services

#### 2. Creating a New Proxy Session
- User clicks "New Session" button in the UI
- A form appears with fields:
  - **Session Name** (e.g., "Payment Flow Testing - Dec 16")
  - **Owner** (auto-filled with current user, editable)
  - **Target Service** (dropdown showing available services from SERVICE_MAP: "payment-api", "user-api", etc.)
  - **Expiration** (optional, defaults to 24 hours)
- User fills in the form and clicks "Create Session"
- Frontend sends the request to backend API: `POST /_proxy-api/sessions`
- Backend creates the session and returns session details
- UI displays a **success notification** with:
  - The generated `sessionId`
  - **Setup instructions** specific to this session
  - A "Copy Session Configuration" button

#### 3. Configuring the Client Application
The UI displays clear instructions for the user:

**Option A - Browser Extension (for web testing):**
- Install a header modification extension (e.g., "ModHeader" or "Simple Modify Headers")
- Add two custom headers:
  - `X-Mock-Target-Service: payment-api` (from the selected service)
  - `X-Mock-Session-Id: abc-123-def-456` (the generated sessionId)
- Configure the client app to point to the proxy URL: `http://proxy.company.com:8080`

**Option B - Code Configuration (for automated tests):**
- UI provides code snippets in multiple languages:
  ```javascript
  // JavaScript/Node.js
  const proxyConfig = {
    baseURL: 'http://proxy.company.com:8080',
    headers: {
      'X-Mock-Target-Service': 'payment-api',
      'X-Mock-Session-Id': 'abc-123-def-456'
    }
  };
  ```

**Option C - Curl Example:**
```bash
curl -H "X-Mock-Target-Service: payment-api" \
     -H "X-Mock-Session-Id: abc-123-def-456" \
     http://proxy.company.com:8080/api/v1/payments
```

User copies the configuration and applies it to their client application.

#### 4. Session Dashboard - Monitoring Traffic in Real-Time
- User clicks on the newly created session to open its dedicated dashboard
- The session dashboard displays:
  - **Session Info Panel**: Name, status, owner, created time, expires time
  - **Live Traffic Feed**: A real-time list of HTTP requests flowing through the proxy
    - WebSocket connection streams new traffic as it happens
    - Each row shows: timestamp, method, path, status code, duration
    - Color coding: green for 2xx, yellow for 3xx, orange for 4xx, red for 5xx
  - **Filters Bar**: Quick filters for method, status, path search
  - **Stats Panel**: Total requests, success rate, average response time

#### 5. Making Requests Through the Proxy
- User runs their client application (tests, manual browser testing, API calls, etc.)
- Each request from the client:
  1. Goes to the proxy server with the two required headers
  2. Proxy validates the `X-Mock-Target-Service` header against SERVICE_MAP
  3. If valid, forwards the request to the target service (e.g., `https://payment.prod.company.com`)
  4. Captures the full request and response
  5. Associates it with the session via `X-Mock-Session-Id`
  6. Returns the response to the client
  7. **Immediately** pushes the traffic event to the frontend via WebSocket
- User sees each request appear in real-time in the Traffic Feed panel

#### 6. Inspecting Traffic Details
- User clicks on any request in the Traffic Feed
- A detailed panel slides in showing:
  - **Request Tab**: Method, full URL, headers, body (formatted JSON/XML)
  - **Response Tab**: Status code, headers, body (formatted and syntax-highlighted)
  - **Timing Tab**: Total duration, breakdown if available
  - **Actions**: 
    - "Create Stub from This Request" button
    - "Export as cURL" button
    - "Copy Request/Response" buttons

#### 7. Creating Stubs - Method A: From Recorded Traffic
- User spots a failing or slow request in the traffic feed
- Clicks on the request, then clicks "Create Stub from This Request"
- A stub creation form opens, pre-populated with:
  - Request matching: Method and URL pattern from the selected request
  - Response: Status, headers, and body from the actual response
- User can customize:
  - **Response Body**: Edit the JSON/XML to return different data
  - **Status Code**: Change from 200 to 500 to simulate errors
  - **Response Delay**: Add artificial latency (e.g., 2000ms)
  - **Ephemeral Settings**:
    - "Apply once" (uses: 1) vs "Apply N times" vs "Apply forever" (uses: null)
    - TTL: "Expire after 1 hour" or custom duration
  - **Priority**: Higher priority stubs match first
- User clicks "Create Stub"
- Frontend sends request to `POST /_proxy-api/stubs` with the `X-Mock-Session-Id` header
- Stub is created and appears in the "Active Stubs" panel
- A notification confirms: "Stub created! Next matching request will use this stub."

#### 8. Creating Stubs - Method B: Manual Creation
- User clicks "Create Stub" button in the session dashboard
- A blank stub creation form opens:
  - **Request Matching**:
    - HTTP Method dropdown (GET, POST, PUT, DELETE, etc.)
    - URL Pattern: Exact match or pattern with wildcards
    - Optional: Header matchers, body matchers (JSON path, regex)
  - **Response Definition**:
    - Status code input
    - Headers (key-value pairs)
    - Body (with syntax highlighting for JSON/XML)
    - Delay (milliseconds)
  - **Ephemeral Settings** (same as Method A)
  - **Priority** (lower number = higher priority)
- User fills in the form and clicks "Create Stub"
- Stub is created and becomes active

#### 9. Stub Interception in Action
- User's client makes another request that matches a stub
- The proxy:
  1. Checks if any stub matches the request (URL, method, headers, session)
  2. Finds the matching stub (highest priority if multiple match)
  3. Checks ephemeral conditions (TTL not expired, uses > 0)
  4. Returns the stubbed response instead of proxying to upstream
  5. Decrements the stub's uses counter if applicable
  6. Records the traffic with a visual indicator that it was stubbed
- In the UI, the stubbed request appears with:
  - A special icon/badge: "🎭 Stubbed"
  - Different background color to distinguish from real proxied requests
- User can see immediately that their stub is working

#### 10. Managing Stubs
- The "Active Stubs" panel in the session dashboard shows all stubs
- Each stub card displays:
  - Method and URL pattern
  - Status: Active, Expired (TTL), or Exhausted (uses = 0)
  - Remaining uses (if applicable)
  - Expires at timestamp (if TTL set)
  - Match count: How many times this stub has been applied
- User can:
  - **Edit** a stub: Modify response, extend TTL, increase uses
  - **Delete** a stub: Remove permanently
  - **Clone** a stub: Duplicate and modify for similar scenarios

#### 11. Filtering and Analyzing Traffic
- User applies filters in the Traffic Feed:
  - **Method Filter**: Show only POST requests
  - **Status Filter**: Show only 4xx/5xx errors
  - **Path Search**: Enter "/payment" to see only payment-related requests
  - **Stubbed vs Real**: Toggle to show only stubbed or only proxied requests
- User can:
  - **Sort** by timestamp, duration, status
  - **Export** filtered traffic as NDJSON or JSON for offline analysis
  - **Clear** all traffic from the session (useful for a fresh test run)

#### 12. Sharing and Collaboration
- User wants to share findings with their team
- Clicks "Share Session" button
- UI provides:
  - A shareable link to the session dashboard (read-only or full access)
  - Option to export session configuration (stubs + setup instructions)
  - Option to export all traffic as a report
- Team member opens the shared link and sees the same traffic feed and stubs

#### 13. Session Completion
When testing is done:
- User clicks "Close Session" button
- A confirmation dialog appears: "Are you sure? Traffic will still be available for 48 hours."
- User confirms
- Frontend sends `PATCH /_proxy-api/sessions/close` with sessionId
- Session status changes to "CLOSED"
- Session appears in the "Closed Sessions" list
- No new traffic is accepted for this session
- User can still view historical traffic for the next 48 hours

#### 14. Session Expiration (Automatic)
- After 24 hours (or custom expiration time):
  - Backend automatically marks the session as EXPIRED
  - UI shows a banner: "This session expired on [timestamp]"
  - User can still view traffic and stubs (read-only) for review
- After 48 hours total:
  - Session data is purged from the system
  - Session appears in archived state or is removed from the UI

---

### Typical Real-World Scenario

**Scenario**: A QA engineer needs to test how the frontend handles payment API failures

1. **Setup** (2 minutes):
   - Opens Wire Mock Proxy UI
   - Creates session: "Payment Error Testing - Dec 16"
   - Selects target service: "payment-api"
   - Copies the session headers to browser extension
   - Points test environment to proxy server

2. **Capture Normal Flow** (5 minutes):
   - Runs through payment flow in the application
   - Watches requests appear in real-time in the UI
   - Sees successful payment: `POST /api/v1/payments → 200 OK`
   - Reviews the request/response bodies

3. **Create Error Stub** (2 minutes):
   - Clicks on the successful payment request
   - Clicks "Create Stub from This Request"
   - Changes status code from 200 to 503
   - Changes response body to: `{"error": "service_unavailable", "retry_after": 30}`
   - Sets: "Apply 5 times" (to test multiple retry attempts)
   - Clicks "Create Stub"

4. **Test Error Handling** (10 minutes):
   - Attempts payment again in the application
   - Sees 503 error returned instantly (stubbed, not from real API)
   - Verifies the application shows proper error message to user
   - Attempts 4 more times, each time seeing the stubbed error
   - On the 6th attempt, stub is exhausted, request goes to real API (succeeds)

5. **Document and Share** (3 minutes):
   - Exports traffic showing all 6 requests
   - Clicks "Share Session" and sends link to developer
   - Developer reviews the exact requests/responses that reproduced the bug

6. **Cleanup**:
   - Closes session
   - Removes headers from browser extension

**Total Time**: ~20 minutes to thoroughly test error handling that would be difficult to reproduce with the real API


