import React, { useState } from 'react'

export default function BorrowRecordSearch() {
  const [itemId, setItemId] = useState('')
  const [borrowedByUserId, setBorrowedByUserId] = useState('')
  const [active, setActive] = useState(false)
  const [overdue, setOverdue] = useState(false)
  const [results, setResults] = useState([])
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [loading, setLoading] = useState(false)

  async function search(e) {
    e?.preventDefault()
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (itemId) params.append('itemId', itemId)
      if (borrowedByUserId) params.append('borrowedByUserId', borrowedByUserId)
      if (active) params.append('active', 'true')
      if (overdue) params.append('overdue', 'true')
      params.append('page', page)
      params.append('size', size)

      const res = await fetch(`/api/borrow-records/search?${params.toString()}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const json = await res.json()
      setResults(json.content || json)
    } catch (err) {
      console.error('Search error', err)
      setResults([])
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="borrow-record-search">
      <form onSubmit={search} style={{display:'flex',gap:8,flexWrap:'wrap'}}>
        <input placeholder="Item ID" value={itemId} onChange={e => setItemId(e.target.value)} />
        <input placeholder="Borrowed By User ID" value={borrowedByUserId} onChange={e => setBorrowedByUserId(e.target.value)} />
        <label><input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} /> Active</label>
        <label><input type="checkbox" checked={overdue} onChange={e => setOverdue(e.target.checked)} /> Overdue</label>
        <button type="submit">Search</button>
      </form>

      <div className="pagination-controls" style={{marginTop:8}}>
        <label>Page: <input type="number" value={page} min={0} onChange={e => setPage(Number(e.target.value))} /></label>
        <label>Size: <input type="number" value={size} min={1} onChange={e => setSize(Number(e.target.value))} /></label>
        <button onClick={search} disabled={loading}>Go</button>
      </div>

      {loading && <p>Loading…</p>}

      <ul className="results">
        {results.length === 0 && !loading && <li>No results</li>}
        {results.map(r => (
          <li key={r.id}>
            <div><strong>Record #{r.id}</strong></div>
            <div>Item: {r.item?.title || (r.itemId ? `#${r.itemId}` : '—')}</div>
            <div>Borrowed By: {r.borrowedByUserId}</div>
            <div>Borrowed At: {r.borrowedAt}</div>
            <div>Due At: {r.dueAt}</div>
            <div>Returned At: {r.returnedAt || '—'}</div>
          </li>
        ))}
      </ul>
    </section>
  )
}
