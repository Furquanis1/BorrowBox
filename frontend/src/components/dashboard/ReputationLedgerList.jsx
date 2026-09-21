import React from 'react'

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString()
}

function roleLabel(event) {
  return event.role === 'BORROWER' ? 'Borrowed' : 'Lent'
}

function getBadge(event) {
  switch (event.eventType) {
    case 'RETURN_DISPUTED':
      return { className: 'badge badge-warning', label: 'Disputed' }
    case 'LOAN_COMPLETED':
      if (event.onTime) return { className: 'badge badge-success', label: 'On time' }
      if (event.onTime === false) return { className: 'badge badge-danger', label: 'Late' }
      return { className: 'badge badge-info', label: 'Completed' }
    default:
      return { className: 'badge badge-secondary', label: 'Unknown' }
  }
}

const STYLE = {
  wrapper: { marginTop: '1.5rem' },
}

export default function ReputationLedgerList({ events }) {
  const sorted = (events || [])
    .slice()
    .sort((a, b) => new Date(b.occurredAt) - new Date(a.occurredAt))

  return (
    <section className="reputation-ledger" aria-label="Reputation ledger" style={STYLE.wrapper}>
      <div className="requests-header">
        <h2>Reputation ledger</h2>
        <p>Append-only reputation events (ADR-020 / ADR-021).</p>
      </div>

      {sorted.length === 0 ? (
        <p className="empty-block">No reputation events yet in this scope.</p>
      ) : (
        <ul className="transaction-list">
          {sorted.map((event) => {
            const badge = getBadge(event)
            return (
              <li className="transaction-card" key={event.id}>
                <div className="transaction-card-main">
                  <h3>{roleLabel(event)}</h3>
                  <p className="transaction-card-subline">
                    {event.communityName || 'Community'}
                  </p>
                </div>
                <span className={badge.className}>{badge.label}</span>
                <p className="transaction-card-note">{formatDate(event.occurredAt)}</p>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
