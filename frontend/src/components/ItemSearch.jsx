import React, { useState } from 'react'

export default function ItemSearch() {
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const [results, setResults] = useState([])
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [loading, setLoading] = useState(false)
  const [formMode, setFormMode] = useState('create')
  const [editingId, setEditingId] = useState(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)
  const [selectedItem, setSelectedItem] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')

  async function search(e) {
    e?.preventDefault()
    setLoading(true)
    setMessage('')
    try {
      const params = new URLSearchParams()
      if (q) params.append('q', q)
      if (status) params.append('status', status)
      params.append('page', page)
      params.append('size', size)

      const res = await fetch(`/api/items/search?${params.toString()}`)
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

  async function viewDetails(itemId) {
    setDetailLoading(true)
    setDetailError('')
    try {
      const response = await fetch(`/api/items/${itemId}`)
      if (!response.ok) {
        const body = await response.json().catch(() => null)
        throw new Error(body?.error || body?.message || `HTTP ${response.status}`)
      }
      const payload = await response.json()
      setSelectedItem(payload)
    } catch (err) {
      setSelectedItem(null)
      setDetailError(err.message || 'Failed to load item details')
    } finally {
      setDetailLoading(false)
    }
  }

  function startCreate() {
    setFormMode('create')
    setEditingId(null)
    setTitle('')
    setDescription('')
    setMessage('Ready to create a new item.')
  }

  function startEdit(item) {
    setFormMode('edit')
    setEditingId(item.id)
    setTitle(item.title || '')
    setDescription(item.description || '')
    setMessage(`Editing item #${item.id}`)
  }

  async function saveItem(e) {
    e?.preventDefault()
    setSaving(true)
    setMessage('')

    try {
      const payload = {
        title,
        description: description || null
      }

      const response = await fetch(formMode === 'edit' ? `/api/items/${editingId}` : '/api/items', {
        method: formMode === 'edit' ? 'PUT' : 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      })

      if (!response.ok) {
        const body = await response.json().catch(() => null)
        throw new Error(body?.error || body?.message || `HTTP ${response.status}`)
      }

      const saved = await response.json()
      setMessage(formMode === 'edit' ? `Updated item #${saved.id}` : `Created item #${saved.id}`)
      setFormMode('create')
      setEditingId(null)
      setTitle('')
      setDescription('')
      await search()
    } catch (err) {
      setMessage(err.message || 'Failed to save item')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel item-search">
      <div className="panel-heading">
        <div>
          <h2>Items</h2>
          <p className="muted">Search, create, and edit items directly from the demo.</p>
        </div>
        <button onClick={startCreate}>New Item</button>
      </div>

      <form className="stacked-form" onSubmit={saveItem}>
        <label>
          Title
          <input placeholder="Cordless Drill" value={title} onChange={e => setTitle(e.target.value)} required />
        </label>
        <label>
          Description
          <textarea placeholder="Item details" value={description} onChange={e => setDescription(e.target.value)} rows={4} />
        </label>
        <div className="form-actions">
          <button type="submit" disabled={saving}>{saving ? 'Saving…' : formMode === 'edit' ? 'Update Item' : 'Create Item'}</button>
          {formMode === 'edit' && <button type="button" className="secondary-button" onClick={startCreate}>Cancel Edit</button>}
        </div>
      </form>

      {message && <p className="muted form-message">{message}</p>}

      <div className="search-block">
        <form onSubmit={search}>
          <input placeholder="Search query" value={q} onChange={e => setQ(e.target.value)} />
          <select value={status} onChange={e => setStatus(e.target.value)}>
            <option value="">Any status</option>
            <option value="AVAILABLE">AVAILABLE</option>
            <option value="BORROWED">BORROWED</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
          <button type="submit">Search</button>
        </form>

        <div className="pagination-controls">
          <label>Page: <input type="number" value={page} min={0} onChange={e => setPage(Number(e.target.value))} /></label>
          <label>Size: <input type="number" value={size} min={1} onChange={e => setSize(Number(e.target.value))} /></label>
          <button onClick={search} disabled={loading}>Go</button>
        </div>

        {loading && <p>Loading…</p>}

        <div className="list-detail-grid">
          <ul className="results">
            {results.length === 0 && !loading && <li>No results</li>}
            {results.map(item => (
              <li key={item.id}>
                <div className="result-header">
                  <strong>{item.title}</strong>
                  <div className="result-actions">
                    <button type="button" className="secondary-button" onClick={() => viewDetails(item.id)}>View</button>
                    <button type="button" className="secondary-button" onClick={() => startEdit(item)}>Edit</button>
                  </div>
                </div>
                <div>{item.description}</div>
              </li>
            ))}
          </ul>

          <aside className="detail-panel">
            <div className="panel-heading">
              <div>
                <h3>Item Details</h3>
                <p className="muted">Inspect the full item payload returned by the API.</p>
              </div>
            </div>
            {detailLoading && <p>Loading details…</p>}
            {detailError && <p className="error">{detailError}</p>}
            {!detailLoading && !detailError && !selectedItem && <p className="muted">Select an item to see its details.</p>}
            {selectedItem && (
              <div className="result-card">
                <pre>{JSON.stringify(selectedItem, null, 2)}</pre>
              </div>
            )}
          </aside>
        </div>
      </div>
    </section>
  )
}
