export async function apiGet<T = unknown>(path: string) {
    const res = await fetch(`/_proxy-api${path}`, {
        headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
        const txt = await res.text().catch(() => '')
        throw new Error(`${res.status} ${res.statusText}${txt ? `: ${txt}` : ''}`)
    }
    try { return (await res.json()) as T } catch { return (await res.text()) as T }
}
