import React, { useState } from 'react'

function formatDateTimeLocal(date) {
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function buildDefaultDates() {
  const now = new Date()
  const due = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000)
  return {
    borrowedAt: formatDateTimeLocal(now),
    dueAt: formatDateTimeLocal(due)
  }
}

export default function BorrowRecordCreate() {
  const defaults = buildDefaultDates()
  const [borrowRequestId, setBorrowRequestId] = useState('')
  const [itemId, setItemId] = useState('')
  const [borrowedByUserId, setBorrowedByUserId] = useState('')
  const [borrowedAt, setBorrowedAt] = useState(defaults.borrowedAt)
  const [dueAt, setDueAt] = useState(defaults.dueAt)
  const [status, setStatus] = useState('idle')
  const [message, setMessage] = useState('')
  const [createdRecord, setCreatedRecord] = useState(null)

  function resetDefaults() {
    const nextDefaults = buildDefaultDates()
    setBorrowedAt(nextDefaults.borrowedAt)
    setDueAt(nextDefaults.dueAt)
  }

  async function createBorrowRecord(e) {
    e.preventDefault()
    setStatus('loading')
    setMessage('')
    setCreatedRecord(null)

    try {
      const response = await fetch('/api/borrow-records', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          borrowRequestId: Number(borrowRequestId),
          itemId: Number(itemId),
          borrowedByUserId: Number(borrowedByUserId),
          borrowedAt,
          dueAt
        })
      })

      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        throw new Error(payload?.error || payload?.message || `HTTP ${response.status}`)
      }

      const payload = await response.json()
      setCreatedRecord(payload)
      setStatus('success')
      setMessage(`Created borrow record #${payload.id}`)
    } catch (err) {
      setStatus('error')
      setMessage(err.message || 'Failed to create borrow record')
    }
  }

  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>Create Borrow Record</h2>
          <p className="muted">Complete a borrow once the request is approved.</p>
        </div>
        <button type="button" onClick={resetDefaults}>Reset Dates</button>
      </div>

      <form className="stacked-form" onSubmit={createBorrowRecord}>
        <label>
          Borrow Request ID
          <input value={borrowRequestId} onChange={e => setBorrowRequestId(e.target.value)} placeholder="1" required />
        </label>
        <label>
          Item ID
          <input value={itemId} onChange={e => setItemId(e.target.value)} placeholder="1" required />
        </label>
        <label>
          Borrowed By User ID
          <input value={borrowedByUserId} onChange={e => setBorrowedByUserId(e.target.value)} placeholder="2" required />
        </label>
        <label>
          Borrowed At
          <input type="datetime-local" value={borrowedAt} onChange={e => setBorrowedAt(e.target.value)} required />
        </label>
        <label>
          Due At
          <input type="datetime-local" value={dueAt} onChange={e => setDueAt(e.target.value)} required />
        </label>
        <button type="submit" disabled={status === 'loading'}>{status === 'loading' ? 'Creating…' : 'Create Borrow Record'}</button>
      </form>

      {message && <p className={status === 'error' ? 'error' : 'muted'}>{message}</p>}

      {createdRecord && (
        <div className="result-card">
          <h3>Created Record</h3>
          <pre>{JSON.stringify(createdRecord, null, 2)}</pre>
        </div>
      )}
    </section>
  )
}
