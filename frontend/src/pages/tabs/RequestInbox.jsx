import React, { useState, useEffect } from 'react'
import { api } from '../../utils/api'

export default function RequestInbox() {
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(false)
  const [filterStatus, setFilterStatus] = useState('PENDING')

  useEffect(() => {
    loadRequests()
  }, [filterStatus])

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

  const handleApprove = async (requestId) => {
    try {
      await api.approveBorrowRequest(requestId)
      loadRequests()
    } catch (err) {
      console.error('Failed to approve request:', err)
    }
  }

  const handleReject = async (requestId) => {
    try {
      await api.rejectBorrowRequest(requestId)
      loadRequests()
    } catch (err) {
      console.error('Failed to reject request:', err)
    }
  }

  return (
    <div className="request-inbox">
      <div className="filters-row">
        <select
          className="filter-select"
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
                  {new Date(request.createdAt).toLocaleString()}
                </small>
              </div>

              {request.status === 'PENDING' && (
                <div className="request-actions">
                  <button 
                    className="btn btn-primary btn-sm"
                    onClick={() => handleApprove(request.id)}
                  >
                    ✓ Approve
                  </button>
                  <button 
                    className="btn btn-outline btn-sm"
                    onClick={() => handleReject(request.id)}
                  >
                    ✕ Reject
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
