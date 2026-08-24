import React from 'react'
import { useApp } from '../contexts/AppContext'

export default function ItemList({ items = [], loading = false, currentUserId = null, emptyMessage = 'No items found.' }) {
  const { openBorrowModal } = useApp()

  if (loading) {
    return (
      <div className="loading" style={{ textAlign: 'center', padding: '40px', color: '#6B7280' }}>
        Loading items...
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className="empty-state" style={{ textAlign: 'center', padding: '48px 24px', backgroundColor: '#F9FAFB', borderRadius: '12px', color: '#6B7280' }}>
        <p style={{ margin: 0, fontSize: '1rem' }}>{emptyMessage}</p>
      </div>
    )
  }

  return (
    <div className="items-grid">
      {items.map(item => {
        const isOwner = currentUserId && item.ownerId === currentUserId
        const isAvailable = item.status === 'AVAILABLE' || !item.status

        return (
          <div key={item.id} className="item-card">
            <div className="item-header">
              <h3 className="item-title">{item.title}</h3>
              <span
                className={`item-status status-${(item.status || 'AVAILABLE').toLowerCase()}`}
                aria-label={`Status: ${item.status || 'AVAILABLE'}`}
              >
                {item.status || 'AVAILABLE'}
              </span>
            </div>

            <p className="item-description">
              {item.description || 'No description provided.'}
            </p>

            <div className="item-footer">
              <small className="item-meta">
                <span aria-hidden="true">👤 </span>{item.owner?.fullName || (isOwner ? 'You' : `User #${item.ownerId || 'Unknown'}`)}
              </small>

              {!isOwner && (
                <button
                  className="btn btn-sm btn-primary"
                  disabled={!isAvailable}
                  onClick={() => openBorrowModal(item)}
                  aria-label={isAvailable ? `Request to borrow ${item.title}` : `${item.title} is currently borrowed`}
                >
                  {isAvailable ? 'Request' : 'Borrowed'}
                </button>
              )}

              {isOwner && (
                <span style={{ fontSize: '0.75rem', color: '#059669', fontWeight: 600 }} aria-label="You are the owner of this item">
                  Your Item
                </span>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}
