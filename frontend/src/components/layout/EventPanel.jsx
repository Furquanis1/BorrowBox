import React, { useState, useEffect } from 'react'
import { useEvents } from '../../contexts/EventContext'
import { eventService } from '../../services'

const eventLabels = {
  REQUEST_APPROVED: 'Request approved',
  REQUEST_REJECTED: 'Request rejected',
  REQUEST_CANCELLED: 'Request cancelled',
  HANDOVER_SCHEDULED: 'Handover scheduled',
  LOAN_STARTED: 'Loan started',
  HANDOVER_CONFIRMED: 'Handover confirmed',
  HANDOVER_DISPUTED: 'Handover disputed',
  EXTENSION_REQUESTED: 'Extension requested',
  EXTENSION_APPROVED: 'Extension approved',
  EXTENSION_REJECTED: 'Extension rejected',
  EXTENSION_COUNTERED: 'Extension countered',
  EXTENSION_COUNTER_ACCEPTED: 'Extension counter accepted',
  EXTENSION_COUNTER_REJECTED: 'Extension counter rejected',
  RETURN_INITIATED: 'Return initiated',
  RETURN_REPORTED: 'Return reported',
  LOAN_COMPLETED: 'Loan completed',
  RETURN_DISPUTED: 'Return disputed',
  WAITLIST_PROMOTED: 'Promoted from waitlist'
}

const statusLabels = {
  UNREAD: 'Unread',
  READ: 'Read',
  DISMISSED: 'Dismissed'
}

const navigationMap = {
  REQUEST_APPROVED: '/me/requests',
  REQUEST_REJECTED: '/me/requests',
  REQUEST_CANCELLED: '/me/requests',
  HANDOVER_SCHEDULED: '/me/loans',
  LOAN_STARTED: '/me/loans',
  HANDOVER_CONFIRMED: '/me/loans',
  HANDOVER_DISPUTED: '/me/loans',
  EXTENSION_REQUESTED: '/me/loans',
  EXTENSION_APPROVED: '/me/loans',
  EXTENSION_REJECTED: '/me/loans',
  EXTENSION_COUNTERED: '/me/loans',
  EXTENSION_COUNTER_ACCEPTED: '/me/loans',
  EXTENSION_COUNTER_REJECTED: '/me/loans',
  RETURN_INITIATED: '/me/loans',
  RETURN_REPORTED: '/me/loans',
  LOAN_COMPLETED: '/me/loans',
  RETURN_DISPUTED: '/me/loans',
  WAITLIST_PROMOTED: '/me/requests'
}

export default function EventPanel({ onClose }) {
  const { deliveries, unreadCount, fetchDeliveries, markRead, dismiss, markAllRead } = useEvents()
  const [filter, setFilter] = useState('all')
  const [expandedId, setExpandedId] = useState(null)

  const filteredDeliveries = deliveries.filter(d => {
    if (filter === 'all') return true
    return d.status === filter
  })

  const handleOpen = async (delivery) => {
    await markRead(delivery.deliveryId)
    const targetPath = navigationMap[delivery.eventType] || '/me/requests'
    window.location.href = targetPath
    onClose?.()
  }

  const handleDismiss = async (delivery) => {
    await dismiss(delivery.deliveryId)
  }

  const handleMarkAllRead = async () => {
    await markAllRead()
  }

  return (
    <div className="event-panel">
      <div className="event-panel-header">
        <h3>Events</h3>
        <span className="event-panel-badge" style={{ display: unreadCount > 0 ? 'inline-flex' : 'none' }}>
          {unreadCount}
        </span>
      </div>

      <div className="event-panel-toolbar">
        <div className="event-panel-filters">
          <button
            className={filter === 'all' ? 'active' : ''}
            onClick={() => setFilter('all')}
          >
            All
          </button>
          <button
            className={filter === 'UNREAD' ? 'active' : ''}
            onClick={() => setFilter('UNREAD')}
          >
            Unread
          </button>
          <button
            className={filter === 'READ' ? 'active' : ''}
            onClick={() => setFilter('READ')}
          >
            Read
          </button>
          <button
            className={filter === 'DISMISSED' ? 'active' : ''}
            onClick={() => setFilter('DISMISSED')}
          >
            Dismissed
          </button>
        </div>

        {unreadCount > 0 && (
          <button
            className="btn btn-sm btn-secondary"
            onClick={handleMarkAllRead}
          >
            Mark all read
          </button>
        )}
      </div>

      <div className="event-panel-list">
        {filteredDeliveries.length === 0 ? (
          <div className="event-panel-empty">
            {filter === 'all' ? 'No events yet' : `No ${filter.toLowerCase()} events`}
          </div>
        ) : (
          filteredDeliveries.map(delivery => (
            <EventPanelRow
              key={delivery.deliveryId}
              delivery={delivery}
              expanded={expandedId === delivery.deliveryId}
              onToggleExpand={() => setExpandedId(expandedId === delivery.deliveryId ? null : delivery.deliveryId)}
              onOpen={() => handleOpen(delivery)}
              onDismiss={() => handleDismiss(delivery)}
            />
          ))
        )}
      </div>
    </div>
  )
}

function EventPanelRow({ delivery, expanded, onToggleExpand, onOpen, onDismiss }) {
  const label = eventLabels[delivery.eventType] || delivery.eventType

  return (
    <div className={`event-panel-row ${delivery.status.toLowerCase()} ${expanded ? 'expanded' : ''}`}>
      <div
        className="event-panel-row-main"
        onClick={() => (delivery.status === 'READ' ? onToggleExpand() : onOpen())}
      >
        <div className="event-panel-row-header">
          <span className={`event-panel-row-status status-${delivery.status.toLowerCase()}`}>
            {statusLabels[delivery.status]}
          </span>
          <h4 className="event-panel-row-event">{label}</h4>
          <time className="event-panel-row-time" dateTime={delivery.eventCreatedAt}>
            {new Date(delivery.eventCreatedAt).toLocaleString()}
          </time>
        </div>

        <div className="event-panel-row-details">
          <span className="event-panel-row-asset">{delivery.assetTitle}</span>
          <span className="event-panel-row-community">{delivery.communityName}</span>
          {delivery.actorName && (
            <span className="event-panel-row-actor">From {delivery.actorName}</span>
          )}
        </div>
      </div>

      <div className="event-panel-row-actions">
        {delivery.status === 'UNREAD' && (
          <button
            className="btn btn-sm btn-primary"
            onClick={(e) => { e.stopPropagation(); onOpen(); }}
          >
            Open
          </button>
        )}
        {delivery.status !== 'DISMISSED' && (
          <button
            className="btn btn-sm btn-outline"
            onClick={(e) => { e.stopPropagation(); onDismiss(); }}
          >
            {delivery.status === 'UNREAD' ? 'Later' : 'Dismiss'}
          </button>
        )}
        {delivery.status === 'DISMISSED' && (
          <button
            className="btn btn-sm btn-outline"
            onClick={(e) => { e.stopPropagation(); onOpen(); }}
          >
            Open
          </button>
        )}
        <button
          className="btn btn-sm btn-ghost"
          onClick={(e) => { e.stopPropagation(); onToggleExpand(); }}
          aria-label={expanded ? 'Collapse' : 'Expand'}
        >
          {expanded ? '▲' : '▼'}
        </button>
      </div>

      {expanded && (
        <div className="event-panel-row-expanded">
          {delivery.payload && (
            <pre className="event-panel-payload">{delivery.payload}</pre>
          )}
          <div className="event-panel-row-meta">
            <span>Event ID: {delivery.eventId}</span>
            <span>Delivery ID: {delivery.deliveryId}</span>
            <span>Transaction ID: {delivery.transactionId}</span>
          </div>
        </div>
      )}
    </div>
  )
}