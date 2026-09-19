import React from 'react'
import { useReducedMotion } from '../../hooks/useReducedMotion'

export default function EventToast({
  delivery,
  onOpen,
  onLater,
  onClose,
  reducedMotion
}) {
  const prefersReduced = useReducedMotion()

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

  const label = eventLabels[delivery.eventType] || delivery.eventType

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      e.stopPropagation()
      onLater()
    } else if (e.key === 'Enter') {
      e.preventDefault()
      e.stopPropagation()
      onOpen()
    }
  }

  return (
    <div
      className={`event-envelope ${prefersReduced ? 'reduced-motion' : ''}`}
      role="alertdialog"
      aria-live="polite"
      aria-label={`Event: ${label}`}
      onKeyDown={handleKeyDown}
    >
      <div className="event-envelope-content">
        <div className="event-envelope-header">
          <span className="event-envelope-icon">📬</span>
          <h3 className="event-envelope-title">New event</h3>
        </div>

        <div className="event-envelope-body">
          <p className="event-envelope-event">{label}</p>
          <p className="event-envelope-asset">{delivery.assetTitle}</p>
          <p className="event-envelope-community">{delivery.communityName}</p>
          {delivery.actorName && (
            <p className="event-envelope-actor">From {delivery.actorName}</p>
          )}
          <time className="event-envelope-time" dateTime={delivery.eventCreatedAt}>
            {new Date(delivery.eventCreatedAt).toLocaleString()}
          </time>
        </div>

        <div className="event-envelope-actions">
          <button
            type="button"
            className="btn btn-primary event-envelope-open"
            onClick={onOpen}
          >
            Open
          </button>
          <button
            type="button"
            className="btn btn-secondary event-envelope-later"
            onClick={onLater}
          >
            Later
          </button>
          <button
            type="button"
            className="btn btn-ghost event-envelope-close"
            onClick={onClose}
            aria-label="Dismiss"
          >
            ×
          </button>
        </div>
      </div>
    </div>
  )
}