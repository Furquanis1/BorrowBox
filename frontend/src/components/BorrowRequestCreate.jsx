import React, { useState } from 'react'

export default function BorrowRequestCreate() {
  const [itemId, setItemId] = useState('')
  const [requestedByUserId, setRequestedByUserId] = useState('')
  const [message, setMessage] = useState('')
  const [status, setStatus] = useState('idle')
  const [error, setError] = useState('')
  const [createdRequest, setCreatedRequest] = useState(null)

  async function createBorrowRequest(e) {
    e.preventDefault()
    setStatus('loading')
    setError('')
    setCreatedRequest(null)

    try {
      const response = await fetch('/api/borrow-requests', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          itemId: Number(itemId),
          requestedByUserId: Number(requestedByUserId),
          message: message || null
        })
      })

      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        const messageText = payload?.error || payload?.message || `HTTP ${response.status}`
        throw new Error(messageText)
      }

      const payload = await response.json()
      setCreatedRequest(payload)
      setStatus('success')
    } catch (err) {
      setStatus('error')
      setError(err.message || 'Failed to create borrow request')
    }
  }

  return (
    <section className="panel">
      <h2>Create Borrow Request</h2>
      <p className="muted">Submit a request for an available item using an item ID and user ID.</p>

      <form className="stacked-form" onSubmit={createBorrowRequest}>
        <label>
          Item ID
          <input value={itemId} onChange={e => setItemId(e.target.value)} placeholder="1" required />
        </label>
        <label>
          Requested By User ID
          <input value={requestedByUserId} onChange={e => setRequestedByUserId(e.target.value)} placeholder="2" required />
        </label>
        <label>
          Message
          <textarea value={message} onChange={e => setMessage(e.target.value)} placeholder="Please approve this request" rows={4} />
        </label>
        <button type="submit" disabled={status === 'loading'}>{status === 'loading' ? 'Creating…' : 'Create Request'}</button>
      </form>

      {status === 'error' && <p className="error">{error}</p>}
      {status === 'success' && createdRequest && (
        <div className="result-card">
          <h3>Created</h3>
          <pre>{JSON.stringify(createdRequest, null, 2)}</pre>
        </div>
      )}
    </section>
  )
}
