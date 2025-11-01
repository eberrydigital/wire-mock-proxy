import React, {useState} from 'react'
import { Routes, Route, NavLink } from 'react-router-dom'
import {apiGet} from "./api.ts";

function Nav() {
    const linkStyle: React.CSSProperties = { marginRight: 12 }
    return (
        <nav style={{ padding: 12, borderBottom: '1px solid #eee' }}>
            <NavLink to="/" style={linkStyle}>Dashboard</NavLink>
            <NavLink to="/requests" style={linkStyle}>Requests</NavLink>
            <NavLink to="/stubs" style={linkStyle}>Stubs</NavLink>
        </nav>
    )
}

function Dashboard() {
    const [out, setOut] = useState<string>('—')

    const ping = async () => {
        try {
            const data = await apiGet('/status') // ← подставь свой эндпоинт Requests API
            setOut(typeof data === 'string' ? data : JSON.stringify(data, null, 2))
        } catch (e: any) {
            setOut(`Error: ${e?.message ?? String(e)}`)
        }
    }

    return (
        <div>
            <h2>Dashboard</h2>
            <button onClick={ping}>Ping /_proxy-api/status</button>
            <pre style={{ background: '#f6f6f6', padding: 12, marginTop: 12 }}>{out}</pre>
        </div>
    )
}
function Requests()  { return <div>Requests</div> }
function Stubs()     { return <div>Stubs</div> }

export default function App() {
    return (
        <div style={{ fontFamily: 'system-ui, sans-serif' }}>
            <Nav />
            <div style={{ padding: 16 }}>
                <Routes>
                    <Route path="/" element={<Dashboard />} />
                    <Route path="/requests" element={<Requests />} />
                    <Route path="/stubs" element={<Stubs />} />
                </Routes>
            </div>
        </div>
    )
}
