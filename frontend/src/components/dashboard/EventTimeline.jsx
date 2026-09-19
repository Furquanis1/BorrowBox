import React from 'react'

const EVENT_META = {
  REQUEST_APPROVED: { icon: 'bi-check-circle', label: 'Request approved' },
  REQUEST_REJECTED: { icon: 'bi-x-circle', label: 'Request rejected' },
  REQUEST_CANCELLED: { icon: 'bi-slash-circle', label: 'Request cancelled' },
  HANDOVER_SCHEDULED: { icon: 'bi-calendar-event', label: 'Handover scheduled' },
  LOAN_STARTED: { icon: 'bi-box-arrow-right', label: 'Loan started' },
  HANDOVER_CONFIRMED: { icon: 'bi-hand-thumbs-up', label: 'Receipt confirmed' },
  HANDOVER_DISPUTED: { icon: 'bi-exclamation-triangle', label: 'Handover disputed' },
  EXTENSION_REQUESTED: { icon: 'bi-calendar-plus', label: 'Extension requested' },
  EXTENSION_APPROVED: { icon: 'bi-calendar-check', label: 'Extension approved' },
  EXTENSION_REJECTED: { icon: 'bi-calendar-x', label: 'Extension rejected' },
  EXTENSION_COUNTERED: { icon: 'bi-arrow-repeat', label: 'Extension counter offer' },
  EXTENSION_COUNTER_ACCEPTED: { icon: 'bi-check2', label: 'Counter offer accepted' },
  EXTENSION_COUNTER_REJECTED: { icon: 'bi-x', label: 'Counter offer declined' },
  RETURN_INITIATED: { icon: 'bi-arrow-90deg-left', label: 'Return initiated' },
  RETURN_REPORTED: { icon: 'bi-box-arrow-up', label: 'Handback reported' },
  LOAN_COMPLETED: { icon: 'bi-flag', label: 'Loan completed' },
  RETURN_DISPUTED: { icon: 'bi-exclamation-octagon', label: 'Return disputed' },
  WAITLIST_PROMOTED: { icon: 'bi-hourglass-split', label: 'Promoted from waitlist' },
}

function formatDateTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} ${d
    .toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`
}

export default function EventTimeline({ events = [], currentUserId }) {
  if (!events.length) {
    return <p className="timeline-empty">No activity recorded yet.</p>
  }

  return (
    <ol className="timeline">
      {events.map((event) => {
        const meta = EVENT_META[event.eventType] || { icon: 'bi-dot', label: event.eventType }
        const actor =
          event.actorId == null
            ? 'System'
            : event.actorId === currentUserId
              ? 'You'
              : event.actorName

        return (
          <li className="timeline-item" key={event.id}>
            <span className="timeline-marker" aria-hidden="true">
              <i className={`bi ${meta.icon}`} />
            </span>
            <div className="timeline-content">
              <span className="timeline-title">{meta.label}</span>
              <span className="timeline-meta">
                {actor}
                {event.createdAt ? ` · ${formatDateTime(event.createdAt)}` : ''}
              </span>
            </div>
          </li>
        )
      })}
    </ol>
  )
}
