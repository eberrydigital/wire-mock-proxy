// ===== Auth & API helpers =====
const qp = new URLSearchParams(location.search);
const TOKEN = qp.get("token") || null;

function apiHeaders(extra = {}) {
  const base = { Accept: "application/json" };
  if (TOKEN) base["Authorization"] = "Bearer " + TOKEN;
  return { ...base, ...extra };
}

async function apiFetch(input, init = {}) {
  const url = typeof input === "string" ? input : input.toString();
  const res = await fetch(url, { ...init, headers: apiHeaders(init.headers || {}) });
  return res;
}

const API = {
  list: () => "/_proxy-api/requests",
  details: (id) => `/_proxy-api/requests/${encodeURIComponent(id)}`,
  createStub: () => "/_proxy-api/stubs",
};

const qs = (sel, root = document) => root.querySelector(sel);
const qsa = (sel, root = document) => [...root.querySelectorAll(sel)];

// ===== Utilities =====
const fmtTime = (isoOrMs) => {
  try {
    const d = typeof isoOrMs === "number" ? new Date(isoOrMs) : new Date(isoOrMs);
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  } catch {
    return "";
  }
};

const toast = (msg, ms = 2200) => {
  const el = qs("#toast");
  if (!el) return;
  el.textContent = msg;
  el.classList.add("show");
  setTimeout(() => el.classList.remove("show"), ms);
};

function isLikelyJson(contentType) {
  if (!contentType) return false;
  const ct = contentType.toLowerCase();
  return ct.includes("application/json") || ct.endsWith("+json");
}
function safeParseJson(text) {
  try { return JSON.parse(text); } catch { return null; }
}
function prettifyBody(body, contentType) {
  if (isLikelyJson(contentType)) {
    const parsed = typeof body === "string" ? safeParseJson(body) : body;
    return parsed ? JSON.stringify(parsed, null, 2)
                  : (typeof body === "string" ? body : JSON.stringify(body, null, 2));
  }
  return typeof body === "string" ? body : JSON.stringify(body, null, 2);
}

// Loosened URL heuristic
function loosenUrl(pathAndQuery) {
  const [path, query = ""] = (pathAndQuery || "").split("?");
  const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const loPath = path
    .split("/")
    .map((seg) => {
      if (!seg) return "";
      if (/^[0-9]+$/.test(seg)) return "\\d+";
      if (/^[0-9a-fA-F-]{8,}$/.test(seg)) return "[0-9a-fA-F-]{8,}";
      return esc(seg);
    })
    .join("/");
  const loQuery = query ? "(\\?.*)?" : "";
  return `^${loPath}${loQuery}$`;
}

// ===== Requests table =====
let autoRefresh = true;
let refreshTimer = null;
const state = {
  requests: [],
  filters: { path: "", method: "" }
};

function normalizeRequestsPayload(data) {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  if (Array.isArray(data.items)) return data.items;
  if (Array.isArray(data.requests)) return data.requests;
  if (Array.isArray(data.content)) return data.content;
  if (Array.isArray(data.data)) return data.data;
  if (Array.isArray(data.entries)) return data.entries;
  if (Array.isArray(data.events)) return data.events;
  if (Array.isArray(data.logs)) return data.logs;
  if (data._embedded) {
    for (const v of Object.values(data._embedded)) if (Array.isArray(v)) return v;
  }
  if (data.id && (data.method || data.request?.method)) return [data];
  for (const v of Object.values(data)) if (Array.isArray(v) && v.length && typeof v[0] === "object") return v;
  return [];
}

async function fetchRequests() {
  const url = new URL(API.list(), location.origin);

  // (совместимость со старым UI) internal=1 включаем только если есть чекбокс с id=f-internal
  const internalCheckbox = document.getElementById("f-internal");
  if (internalCheckbox && internalCheckbox.checked) {
    url.searchParams.set("internal", "1");
  }

  try {
    const res = await apiFetch(url.toString());
    const ct = (res.headers.get("content-type") || "").toLowerCase();

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      console.warn("Requests load failed:", res.status, text);
      toast("Failed to load requests");
      return;
    }

    let list = [];
    if (ct.includes("json")) {
      const json = await res.json().catch(() => null);
      if (json) list = normalizeRequestsPayload(json);
    } else {
      const text = await res.text();
      // NDJSON fallback
      list = text
        .split("\n")
        .map((l) => l.trim())
        .filter(Boolean)
        .map((l) => { try { return JSON.parse(l); } catch { return null; } })
        .filter(Boolean);
    }

    state.requests = Array.isArray(list) ? list : [];
    renderRequests();
  } catch (e) {
    console.error(e);
    toast("Failed to load requests");
  }
}

function renderRequests() {
  const tbody = qs("#requestsTbody");
  tbody.innerHTML = "";
  const { path, method } = state.filters;

  const rows = state.requests.filter((it) => {
    const url = it.path || it.url || it.request?.url || "";
    const m = (it.method || it.request?.method || "").toUpperCase();
    const okPath = path ? url.toLowerCase().includes(path.toLowerCase()) : true;
    const okMethod = method ? m === method : true;
    return okPath && okMethod;
  });

  if (rows.length === 0) {
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.colSpan = 5;
    td.textContent = "No requests yet.";
    tr.appendChild(td);
    tbody.appendChild(tr);
    return;
  }

  for (const it of rows) {
    const tr = document.createElement("tr");
    tr.dataset.id = it.id || it.requestId || it.uuid || it.correlationId || "";

    const time = it.receivedAt || it.startedAt || it.timestamp || it.requestTimestamp || Date.now();
    const methodText = (it.request?.method || it.method || "").toUpperCase();
    const urlText = it.request?.url || it.path || it.url || "";
    const statusVal = it.response?.status ?? it.status ?? it.responseStatus;
    const badgeCls = statusVal >= 200 && statusVal < 400 ? "ok" : "fail";
    const dur = it.timingMs ?? it.durationMs ?? it.latencyMs ?? it.tookMs ?? "";

    tr.innerHTML = `
      <td>${fmtTime(time)}</td>
      <td><span class="badge">${methodText}</span></td>
      <td title="${urlText}">${urlText}</td>
      <td><span class="badge ${statusVal ? badgeCls : ""}">${statusVal ?? ""}</span></td>
      <td>${dur}</td>
    `;
    tr.addEventListener("click", () => openWizard(tr.dataset.id));
    tbody.appendChild(tr);
  }
}

// ===== Wizard =====
const dialog = qs("#wizardDialog");
const wizard = { requestId: null, request: null, step: 1, matchMode: "LOOSENED" };

function setStep(step) {
  wizard.step = step;
  qsa(".step").forEach((s) => s.classList.toggle("active", Number(s.dataset.step) === step));
  qsa(".step-pane").forEach((p) => (p.hidden = Number(p.dataset.step) !== step));
  qs("#backBtn").disabled = step === 1;
  qs("#nextBtn").hidden = step === 3;
  qs("#createBtn").hidden = step !== 3;
}

async function openWizard(id) {
  if (!id) return;
  wizard.requestId = id;

  try {
    const res = await apiFetch(API.details(id));
    if (!res.ok) throw new Error(`Details HTTP ${res.status}`);
    wizard.request = await res.json();
  } catch (e) {
    console.error(e);
    toast("Failed to load request details");
    return;
  }

  const method = (wizard.request.method || wizard.request.request?.method || "GET").toUpperCase();
  const rawUrl = extractPathAndQuery(wizard.request);

  qs("#wMethod").value = method;
  qs("#matchLoosened").checked = true;
  qs("#matchExact").checked = false;
  qs("#urlLabel").textContent = "Pattern";
  qs("#wUrl").value = loosenUrl(rawUrl);

  const resp = extractResponse(wizard.request);
  const ct = findHeader(resp.headers, "content-type") || "";

  qs("#wStatus").value = resp.status || 200;
  qs("#wContentType").value = ct;
  fillHeadersEditor(resp.headers);

  const bodyText = prettifyBody(resp.body, ct);
  qs("#wBody").value = bodyText;
  qs("#bodyHint").textContent = isLikelyJson(ct)
    ? "Detected JSON. The body will be sent as bodyJson."
    : "Detected TEXT. The body will be sent as bodyText.";

  qs("#createBtn").hidden = true;
  qs("#closeAfterCreateBtn").hidden = true;
  qs("#viewStubsLink").hidden = true;
  qs("#createError").hidden = true;
  qs("#createError").textContent = "";

  setStep(1);
  dialog.showModal();
}

function closeWizard() {
  dialog.close();
  wizard.requestId = null;
  wizard.request = null;
  wizard.step = 1;
}

function extractPathAndQuery(req) {
  const path = req.path || req.url || req.request?.url || req.request?.path || req.request?.absoluteUrl || "";
  try {
    if (path.startsWith("http://") || path.startsWith("https://")) {
      const u = new URL(path);
      return u.pathname + (u.search || "");
    }
  } catch {}
  return path;
}

function extractResponse(req) {
  const r = req.response || req.responseDefinition || req;
  return {
    status: r.status || r.responseStatus || 200,
    headers: r.headers || r.responseHeaders || {},
    body: r.body != null ? r.body : r.responseBody || "",
  };
}

function findHeader(headers, name) {
  if (!headers) return "";
  const n = name.toLowerCase();
  for (const k of Object.keys(headers)) if (k.toLowerCase() === n) return headers[k];
  return "";
}

// Headers editor
function fillHeadersEditor(headers = {}) {
  const wrap = qs("#headersEditor");
  wrap.innerHTML = "";
  const entries = Object.entries(headers);
  const initial = entries.length ? entries : [["Content-Type", qs("#wContentType").value || ""]];
  for (const [k, v] of initial) addHeaderRow(k, v);
}
function addHeaderRow(key = "", val = "") {
  const wrap = qs("#headersEditor");
  const k = document.createElement("input"); k.type = "text"; k.placeholder = "Header-Name"; k.value = key;
  const v = document.createElement("input"); v.type = "text"; v.placeholder = "value"; v.value = val;
  const rm = document.createElement("button"); rm.type = "button"; rm.textContent = "×"; rm.className = "kv-remove";
  const row = document.createElement("div"); row.className = "kv-row";
  rm.addEventListener("click", () => row.remove());
  row.appendChild(k); row.appendChild(v); row.appendChild(rm);
  wrap.appendChild(row);
}
qs("#addHeaderBtn").addEventListener("click", () => addHeaderRow());

// Mode switch
qsa('input[name="matchMode"]').forEach((r) => {
  r.addEventListener("change", () => {
    if (!r.checked) return;
    const rawUrl = extractPathAndQuery(wizard.request || {});
    if (r.value === "EXACT") {
      qs("#urlLabel").textContent = "URL";
      qs("#wUrl").value = rawUrl;
      wizard.matchMode = "EXACT";
    } else {
      qs("#urlLabel").textContent = "Pattern";
      qs("#wUrl").value = loosenUrl(rawUrl);
      wizard.matchMode = "LOOSENED";
    }
  });
});

// Navigation
qs("#nextBtn").addEventListener("click", () => {
  if (wizard.step === 1) {
    if (!qs("#wUrl").value.trim()) return toast("Enter URL/pattern");
    setStep(2);
  } else if (wizard.step === 2) {
    updateSummaryPreview();
    setStep(3);
  }
});
qs("#backBtn").addEventListener("click", () => setStep(Math.max(1, wizard.step - 1)));
qs("#createBtn").addEventListener("click", onCreateStub);
qs("#closeAfterCreateBtn").addEventListener("click", closeWizard);
qs("#closeWizardBtn").addEventListener("click", closeWizard);

// Build payload & preview
function buildPayload() {
  const method = qs("#wMethod").value.trim().toUpperCase();
  const urlVal = qs("#wUrl").value.trim();
  const matchType = wizard.matchMode;

  const status = Number(qs("#wStatus").value || 200);
  const contentType = qs("#wContentType").value.trim();
  const bodyText = qs("#wBody").value;

  const headers = {};
  qsa("#headersEditor .kv-row").forEach((row) => {
    const [k, v] = qsa("input", row);
    if (k.value.trim()) headers[k.value.trim()] = v.value;
  });
  if (contentType) headers["Content-Type"] = contentType;

  const asJson = isLikelyJson(contentType);
  const payload = {
    request: { method, url: { type: matchType, value: urlVal } },
    response: { mode: "STATIC", status, headers },
    ephemeral: { uses: Number(qs("#wUses").value || 1) },
    priority: 2,
  };

  if (asJson) {
    const parsed = safeParseJson(bodyText);
    payload.response.bodyJson = parsed ?? safeParseJsonFallback(bodyText);
  } else {
    payload.response.bodyText = bodyText;
  }

  const ttl = Number(qs("#wTtl").value);
  if (!Number.isNaN(ttl) && ttl > 0) payload.ephemeral.ttlMs = ttl;

  return payload;
}
function safeParseJsonFallback(text) {
  try { return JSON.parse(text); } catch { return text; }
}

function updateSummaryPreview() {
  const method = qs("#wMethod").value.trim().toUpperCase();
  const urlVal = qs("#wUrl").value.trim();
  const matchType = wizard.matchMode;

  const status = Number(qs("#wStatus").value || 200);
  const contentType = qs("#wContentType").value.trim();
  const bodyLen = (qs("#wBody").value || "").length;

  qs("#sMatch").textContent = matchType === "EXACT"
    ? `${method} url = "${urlVal}"`
    : `${method} urlMatching("${urlVal}")`;

  qs("#sResp").textContent = `status=${status}, body=${isLikelyJson(contentType) ? "JSON" : "TEXT"} (${bodyLen} ch)`;

  const ttl = Number(qs("#wTtl").value);
  qs("#sEph").textContent = `uses=1${(!Number.isNaN(ttl) && ttl>0) ? `, ttlMs=${ttl}` : ""}`;

  qs("#previewJson").value = JSON.stringify(buildPayload(), null, 2);
}

// Create stub
async function onCreateStub() {
  const payload = buildPayload();
  qs("#createBtn").disabled = true;

  try {
    const res = await apiFetch(API.createStub(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`${res.status} ${res.statusText}: ${text}`);
    }
    toast("Stub created");
    qs("#createBtn").hidden = true;
    qs("#closeAfterCreateBtn").hidden = false;
    qs("#viewStubsLink").hidden = false;
  } catch (e) {
    console.error(e);
    const err = qs("#createError");
    err.hidden = false;
    err.textContent = String(e.message || e);
    toast("Failed to create stub", 2600);
  } finally {
    qs("#createBtn").disabled = false;
  }
}

// ===== Controls & filters =====
qs("#refreshBtn").addEventListener("click", fetchRequests);
qs("#autoToggleBtn").addEventListener("click", (e) => {
  autoRefresh = !autoRefresh;
  e.currentTarget.textContent = `Auto: ${autoRefresh ? "On" : "Off"}`;
  e.currentTarget.setAttribute("aria-pressed", String(autoRefresh));
  if (autoRefresh) scheduleRefresh(); else clearInterval(refreshTimer);
});
qs("#filterPath").addEventListener("input", (e) => {
  state.filters.path = e.target.value;
  renderRequests();
});
qs("#filterMethod").addEventListener("change", (e) => {
  state.filters.method = e.target.value || "";
  renderRequests();
});
qs("#clearFiltersBtn").addEventListener("click", () => {
  state.filters = { path: "", method: "" };
  qs("#filterPath").value = "";
  qs("#filterMethod").value = "";
  renderRequests();
});

function scheduleRefresh() {
  clearInterval(refreshTimer);
  refreshTimer = setInterval(() => { if (autoRefresh) fetchRequests(); }, 3000);
}

// ===== Init =====
fetchRequests().then(() => scheduleRefresh());
