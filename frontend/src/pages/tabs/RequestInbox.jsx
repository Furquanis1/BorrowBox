import React, { useState, useEffect } from 'react'
import { api } from '../../utils/api'
import { useApp } from '../../contexts/AppContext'

/**
 * RequestInbox — shows borrow requests and lets item owners confirm or reject them.
 *
 * Confirm (formerly "Approve") uses POST /confirm, which atomically approves the
 * request and creates a BorrowRecord with the chosen due-date.
 * Reject uses POST /reject which sets status to REJECTED and restores item to AVAILABLE.
 */
export default function RequestInbox() {
  const { showToast, triggerRefresh, refreshTrigger } = useApp()
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(false)
  const [filterStatus, setFilterStatus] = useState('PENDING')
  // Per-request due-date state  { [requestId]: isoString }
  const [dueDates, setDueDates] = useState({})
  // Track which request is currently being actioned
  const [actioningId, setActioningId] = useState(null)

  useEffect(() => {
    loadRequests()
  }, [filterStatus, refreshTrigger])

  const loadRequests = async () => {
    setLoading(true)
    try {
      const allRequests = await api.getBorrowRequests()
      const filtered = Array.isArray(allRequests)
        ? allRequests.filter(r => r.status === filterStatus)
        : []
      setRequests(filtered)
    } catch (err) {
      console.error('Failed to load requests:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleConfirm = async (requestId) => {
    const dueAt = dueDates[requestId]
    if (!dueAt) {
      showToast('Please select a due date before confirming', 'error')
      return
    }
    setActioningId(requestId)
    try {
      // dueAt from <input type="date"> is yyyy-MM-dd; backend needs ISO-8601 datetime
      const dueAtIso = new Date(dueAt).toISOString().replace('Z', '')
      await api.confirmBorrowRequest(requestId, dueAtIso)
      showToast(`Request #${requestId} confirmed — item handed off!`)
      triggerRefresh()
      loadRequests()
    } catch (err) {
      showToast(err.message || 'Failed to confirm request', 'error')
    } finally {
      setActioningId(null)
    }
  }

  const handleReject = async (requestId) => {
    setActioningId(requestId)
    try {
      await api.rejectBorrowRequest(requestId)
      showToast(`Request #${requestId} rejected`)
      triggerRefresh()
      loadRequests()
    } catch (err) {
      showToast(err.message || 'Failed to reject request', 'error')
    } finally {
      setActioningId(null)
    }
  }

  // Default due date: 14 days from today
  const defaultDueDate = () => {
    const d = new Date()
    d.setDate(d.getDate() + 14)
    return d.toISOString().split('T')[0]
  }

  return (
    <div className="request-inbox">
      <div className="filters-row" role="search" aria-label="Request filters">
        <select
          className="filter-select"
          aria-label="Filter requests by status"
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
        >
          <option value="PENDING">Pending</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
          <option value="COMPLETED">Completed</option>
        </select>
      </div>

      {loading ? (
        <div className="loading">Loading requests...</div>
      ) : requests.length === 0 ? (
        <div className="empty-state">
          <p>No {filterStatus.toLowerCase()} requests at the moment.</p>
        </div>
      ) : (
        <div className="requests-list">
          {requests.map(request => (
            <div key={request.id} className="request-card">
              <div className="request-header">
                <h4 className="request-title">Borrow Request #{request.id}</h4>
                <span className={`request-status status-${request.status?.toLowerCase()}`}>
                  {request.status}
                </span>
              </div>

              <div className="request-body">
                <p><strong>Requested by:</strong> User #{request.requestedByUserId}</p>
                <p><strong>Item:</strong> Item #{request.itemId}</p>
                {request.message && (
                  <div className="request-message">
                    <strong>Message:</strong>
                    <p>{request.message}</p>
                  </div>
                )}
                <small className="request-date">
                  Submitted: {new Date(request.createdAt).toLocaleString()}
                </small>
              </div>

              {request.status === 'PENDING' && (
                <div className="request-actions" style={{ flexDirection: 'column', gap: '10px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <label
                      htmlFor={`due-date-${request.id}`}
                      style={{ fontSize: '0.8rem', color: 'var(--slate)', whiteSpace: 'nowrap', fontWeight: 500 }}
                    >
                      Due date:
                    </label>
                    <input
                      id={`due-date-${request.id}`}
                      type="date"
                      className="search-input"
                      aria-label={`Due date for request #${request.id}`}
                      style={{ flex: 1, padding: '6px 10px', fontSize: '0.85rem' }}
                      defaultValue={defaultDueDate()}
                      min={new Date().toISOString().split('T')[0]}
                      onChange={(e) =>
                        setDueDates(prev => ({ ...prev, [request.id]: e.target.value }))
                      }
                      onFocus={(e) => {
                        // Pre-populate if not already set
                        if (!dueDates[request.id]) {
                          setDueDates(prev => ({ ...prev, [request.id]: e.target.value || defaultDueDate() }))
                        }
                      }}
                    />
                  </div>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button
                      className="btn btn-primary btn-sm"
                      onClick={() => handleConfirm(request.id)}
                      disabled={actioningId === request.id}
                      aria-label={`Confirm request #${request.id}`}
                    >
                      {actioningId === request.id ? '...' : '✓ Confirm Handoff'}
                    </button>
                    <button
                      className="btn btn-outline btn-sm"
                      onClick={() => handleReject(request.id)}
                      disabled={actioningId === request.id}
                      aria-label={`Reject request #${request.id}`}
                    >
                      ✕ Reject
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
