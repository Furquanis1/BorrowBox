import React, { useState } from 'react'
import { useApp } from '../contexts/AppContext'
import { useAuth } from '../contexts/AuthContext'
import { api } from '../utils/api'

export default function BorrowRequestModal() {
  const { borrowModalItem, closeBorrowModal, showToast, triggerRefresh } = useApp()
  const { user } = useAuth()
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(false)

  if (!borrowModalItem) return null

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!user) {
      showToast('You must be logged in to request an item', 'error')
      return
    }

    setLoading(true)
    try {
      await api.createBorrowRequest(borrowModalItem.id, user.id, message)
      showToast(`Borrow request sent for "${borrowModalItem.title}"!`)
      triggerRefresh()
      closeBorrowModal()
      setMessage('')
    } catch (err) {
      showToast(err.message || 'Failed to submit borrow request', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.5)',
        backdropFilter: 'blur(4px)',
        zIndex: 9000,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '16px'
      }}
      onClick={closeBorrowModal}
    >
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          padding: '24px',
          maxWidth: '480px',
          width: '100%',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
          animation: 'fadeIn 0.2s ease-out'
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <h3 style={{ margin: 0, fontSize: '1.25rem', color: '#111827' }}>
            Request to Borrow
          </h3>
          <button
            onClick={closeBorrowModal}
            style={{ background: 'none', border: 'none', fontSize: '1.25rem', cursor: 'pointer', color: '#6B7280' }}
          >
            ✕
          </button>
        </div>

        <div style={{ backgroundColor: '#F3F4F6', padding: '12px 16px', borderRadius: '8px', marginBottom: '16px' }}>
          <div style={{ fontWeight: 600, color: '#1F2937' }}>{borrowModalItem.title}</div>
          <div style={{ fontSize: '0.875rem', color: '#4B5563', marginTop: '4px' }}>
            {borrowModalItem.description || 'No description provided'}
          </div>
          <div style={{ fontSize: '0.75rem', color: '#6B7280', marginTop: '8px' }}>
            Owner: {borrowModalItem.owner?.fullName || `User #${borrowModalItem.ownerId || 'Unknown'}`}
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 500, color: '#374151', marginBottom: '6px' }}>
              Message for Owner (optional)
            </label>
            <textarea
              rows="3"
              placeholder="Hi, I'd like to borrow this for a couple of days..."
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              style={{
                width: '100%',
                padding: '10px 12px',
                borderRadius: '8px',
                border: '1px solid #D1D5DB',
                fontSize: '0.875rem',
                outline: 'none',
                resize: 'vertical'
              }}
            />
          </div>

          <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
            <button
              type="button"
              onClick={closeBorrowModal}
              className="btn btn-outline"
              disabled={loading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={loading}
            >
              {loading ? 'Sending...' : 'Submit Request'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
