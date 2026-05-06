import React, { useEffect, useState } from 'react'

export default function BorrowRequestList() {
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [actionId, setActionId] = useState(null)

  async function loadRequests() {
    setLoading(true)
    setError('')
    try {
      const response = await fetch('/api/borrow-requests')
      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const payload = await response.json()
      setRequests(Array.isArray(payload) ? payload : [])
    } catch (err) {
      setRequests([])
      setError(err.message || 'Failed to load borrow requests')
    } finally {
      setLoading(false)
    }
  }

  async function approveRequest(id) {
    setActionId(id)
    setError('')
    try {
      const response = await fetch(`/api/borrow-requests/${id}/approve`, { method: 'POST' })
      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        throw new Error(payload?.error || payload?.message || `HTTP ${response.status}`)
      }
      await loadRequests()
    } catch (err) {
      setError(err.message || 'Failed to approve request')
    } finally {
      setActionId(null)
    }
  }

  useEffect(() => {
    loadRequests()
  }, [])

  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>Borrow Requests</h2>
          <p className="muted">View requests and approve pending ones.</p>
        </div>
        <button onClick={loadRequests} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
      </div>

      {error && <p className="error">{error}</p>}

      <ul className="results">
        {!loading && requests.length === 0 && <li>No borrow requests found</li>}
        {requests.map(request => (
          <li key={request.id} className="request-card">
            <div className="request-summary">
              <strong>Request #{request.id}</strong>
              <span className={`status-pill status-${String(request.status || '').toLowerCase()}`}>{request.status}</span>
            </div>
            <div className="request-meta">Message: {request.message || '—'}</div>
            <div className="request-meta">Created: {request.createdAt || '—'}</div>
            <div className="request-actions">
              <button
                onClick={() => approveRequest(request.id)}
                disabled={actionId === request.id || request.status !== 'PENDING'}
              >
                {actionId === request.id ? 'Approving…' : 'Approve'}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
