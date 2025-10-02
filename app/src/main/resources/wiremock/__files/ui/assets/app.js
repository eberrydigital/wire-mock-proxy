(function(){
  const qp = new URLSearchParams(location.search);
  const token = qp.get('token');
  const authHeader = token ? { 'Authorization': 'Bearer ' + token } : {};

  const $ = sel => document.querySelector(sel);
  const tbody = $('#tbl tbody');
  const det = $('#details');

  async function api(path, opts={}){
    opts.headers = Object.assign({'Accept':'application/json'}, authHeader, opts.headers||{});
    const res = await fetch('/_proxy-api' + path, opts);
    if(!res.ok){
      const text = await res.text().catch(()=> '');
      throw new Error(text || (res.status + ' ' + res.statusText));
    }
    // /export отдаёт NDJSON — этот метод не используется для /export
    return await res.json();
  }

  function esc(s){ return String(s); } // для совместимости, используем textContent ниже

  async function load(){
    const params = new URLSearchParams();
    const m = $('#f-method').value;
    const p = $('#f-path').value.trim();
    const s = $('#f-status').value.trim();
    const l = $('#f-limit').value.trim();
    const internal = $('#f-internal').checked;

    if(m) params.set('method', m);
    if(p) params.set('path', p);
    if(s) params.set('status', s);
    if(l) params.set('limit', l);
    if(internal) params.set('internal', '1');

    const data = await api('/requests?' + params.toString());
    tbody.innerHTML = '';

    data.forEach(row => {
      const tr = document.createElement('tr');

      const tdTime = document.createElement('td');
      tdTime.className = 'muted';
      tdTime.textContent = new Date(row.receivedAt).toLocaleTimeString();

      const tdMethod = document.createElement('td');
      tdMethod.textContent = row.request.method;

      const tdUrl = document.createElement('td');
      tdUrl.className = 'url';
      tdUrl.title = row.request.url;
      tdUrl.textContent = row.request.url;

      const tdStatus = document.createElement('td');
      tdStatus.textContent = (row.response.status ?? '').toString();

      const tdMs = document.createElement('td');
      tdMs.textContent = (row.timingMs ?? '').toString();

      tr.append(tdTime, tdMethod, tdUrl, tdStatus, tdMs);
      tr.addEventListener('click', () => show(row.id));
      tbody.appendChild(tr);
    });
  }

  async function show(id){
    const row = await api('/requests/' + id);
    const wrap = document.createElement('div');
    wrap.innerHTML = `
      <h3>Request ${esc(row.request.method)} ${esc(row.request.url)}</h3>
      <div class="two">
        <div>
          <h4>Request headers</h4>
          <pre id="req-h"></pre>
          <h4>Request body</h4>
          <pre id="req-b"></pre>
        </div>
        <div>
          <h4>Response ${esc(row.response.status)}</h4>
          <pre id="res-h"></pre>
          <h4>Response body</h4>
          <pre id="res-b"></pre>
        </div>
      </div>`;
    wrap.querySelector('#req-h').textContent = JSON.stringify(row.request.headers, null, 2);
    wrap.querySelector('#req-b').textContent = row.request.body ?? '';
    wrap.querySelector('#res-h').textContent = JSON.stringify(row.response.headers, null, 2);
    wrap.querySelector('#res-b').textContent = row.response.body ?? '';
    det.innerHTML = '';
    det.appendChild(wrap);
  }

  async function exportNdjson(){
    const res = await fetch('/_proxy-api/export', { headers: authHeader });
    if(!res.ok){ alert('Export failed: ' + (await res.text())); return; }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'requests.jsonl';
    a.click();
    URL.revokeObjectURL(url);
  }

  $('#btn-apply').addEventListener('click', load);
  $('#btn-clear').addEventListener('click', async () => {
    await fetch('/_proxy-api/requests/clear', { method:'POST', headers: authHeader });
    det.innerHTML = '';
    await load();
  });
  $('#btn-export').addEventListener('click', exportNdjson);
  $('#f-internal').addEventListener('change', load);

  load();
  setInterval(load, 3000);
})();
