const PROXY_API = "http://localhost:8333/_proxy-api";
const PROXY_BASE = "http://localhost:8222";
const WS_URL = "ws://localhost:8333/_proxy-api/ws/traffic";

let currentSessionId = null;
let ws = null;

function init() {
    connectWs();
}

function connectWs() {
    ws = new WebSocket(WS_URL);
    const statusDiv = document.getElementById('wsStatus');

    ws.onopen = () => {
        statusDiv.innerText = "Connected ✅";
        statusDiv.style.color = "green";
    };

    ws.onclose = () => {
        statusDiv.innerText = "Disconnected ❌ Reconnecting...";
        statusDiv.style.color = "red";
        setTimeout(connectWs, 3000);
    };

    ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        logTraffic(data);
    };
}

async function createSession() {
    const name = document.getElementById('devName').value;
    const res = await fetch(`${PROXY_API}/sessions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            name: name,
            owner: 'e2e-tester',
            expiresAt: Date.now() + 3600000 // 1 hour
        })
    });

    if (res.ok) {
        const session = await res.json();
        setSession(session);
        // Set WS filter
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ sessionId: session.id }));
        }
    } else {
        alert("Failed to create session: " + await res.text());
    }
}

async function closeSession() {
    if (!currentSessionId) return;

    // Close the session in the database first
    await fetch(`${PROXY_API}/sessions/close`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: currentSessionId })
    });

    // Then notify WebSocket (will trigger server to close WS since session is now closed)
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ sessionId: currentSessionId }));
    }

    currentSessionId = null;
    document.getElementById('sessionParams').style.display = 'block';
    document.getElementById('activeSession').style.display = 'none';
    document.getElementById('sendReqBtn').disabled = true;
}

function setSession(session) {
    currentSessionId = session.id;
    document.getElementById('sessionIdDisplay').innerText = session.id;
    document.getElementById('sessionExpiry').innerText = new Date(session.expiresAt).toLocaleString();

    document.getElementById('sessionParams').style.display = 'none';
    document.getElementById('activeSession').style.display = 'block';
    document.getElementById('sendReqBtn').disabled = false;
}

async function sendRequest(overrideSessionId) {
    const service = document.getElementById('targetService').value;
    const method = document.getElementById('requestMethod').value;
    const path = document.getElementById('requestPath').value;
    const output = document.getElementById('responseOutput');

    const sid = overrideSessionId || currentSessionId;

    output.innerText = "Loading...";

    try {
        const res = await fetch(`${PROXY_BASE}${path}`, {
            method: method,
            headers: {
                'X-Mock-Target-Service': service,
                'X-Mock-Session-Id': sid || ''
            }
        });

        const text = await res.text();
        output.innerHTML = `<span class="status-badge ${res.ok ? 'status-200' : 'status-403'}">${res.status} ${res.statusText}</span>\n\n${text.substring(0, 500)}`;
    } catch (e) {
        output.innerText = "Error: " + e.message;
    }
}

function logTraffic(req) {
    const log = document.getElementById('trafficLog');
    const div = document.createElement('div');
    div.className = 'log-entry';
    const time = new Date(req.timestamp).toLocaleTimeString();
    div.innerHTML = `[${time}] <strong>${req.method}</strong> ${req.path} <span class="status-badge status-${req.responseStatus}">${req.responseStatus}</span>`;
    log.prepend(div);
}

init();
