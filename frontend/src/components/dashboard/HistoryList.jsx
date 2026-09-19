import React from 'react'
import EmptyState from '../ui/EmptyState'

export const HISTORY_STATES = new Set([
  'ACTIVE',
  'RETURN_INITIATED',
  'RETURN_REPORTED',
  'COMPLETED',
  'RETURN_DISPUTED',
])

const STATE_BADGE = {
  ACTIVE: 'badge-teal',
  RETURN_INITIATED: 'badge-warning',
  RETURN_REPORTED: 'badge-info',
  COMPLETED: 'badge-neutral',
  RETURN_DISPUTED: 'badge-danger',
}

const STATE_LABEL = {
  ACTIVE: 'On loan',
  RETURN_INITIATED: 'Return in progress',
  RETURN_REPORTED: 'Return reported',
  COMPLETED: 'Completed',
  RETURN_DISPUTED: 'Return disputed',
}

function formatDate(iso) {
  if (!iso) return null
  return new Date(iso).toLocaleDateString()
}

function activityTime(txn) {
  return new Date(txn.completedAt || txn.startedAt || txn.createdAt || 0).getTime()
}

function HistoryRow({ txn, currentUserId }) {
  const isBorrower = currentUserId && txn.borrowerId === currentUserId
  const counterparty = isBorrower ? txn.lenderName : txn.borrowerName
  const role = isBorrower ? 'Borrowed from' : 'Lent to'

  const returned = formatDate(txn.completedAt)
  const started = formatDate(txn.startedAt)
  const due = formatDate(txn.dueAt)

  return (
    <li className="transaction-card">
      <div className="transaction-card-head">
        <div className="transaction-card-main">
          <h3>{txn.title}</h3>
          <p className="transaction-card-subline">
            {role} {counterparty} · {txn.communityName}
          </p>
          <p className="transaction-card-terms">
            &ldquo;{txn.agreedPurpose || txn.purpose}&rdquo; ·{' '}
            {txn.agreedDurationDays || txn.requestedDurationDays} days
          </p>
        </div>
        <span className={`badge ${STATE_BADGE[txn.state] || 'badge-neutral'}`}>
          {STATE_LABEL[txn.state] || txn.state}
        </span>
      </div>

      <p className="transaction-card-note">
        {txn.state === 'COMPLETED'
          ? `Returned ${returned}.`
          : txn.state === 'RETURN_DISPUTED'
            ? 'Return disputed. This loan is frozen for review.'
            : started
              ? due
                ? `Started ${started} · due ${due}.`
                : `Started ${started}.`
              : 'Not started yet.'}
      </p>
    </li>
  )
}

export default function HistoryList({ transactions, currentUserId }) {
  const history = (transactions || [])
    .filter((txn) => HISTORY_STATES.has(txn.state))
    .slice()
    .sort((a, b) => activityTime(b) - activityTime(a))

  if (history.length === 0) {
    return (
      <EmptyState
        icon="bi-clock-history"
        title="No history yet"
        description="Your borrowing and lending history in this scope will appear here once a request is approved."
      />
    )
  }

  return (
    <ul className="transaction-list">
      {history.map((txn) => (
        <HistoryRow key={txn.id} txn={txn} currentUserId={currentUserId} />
      ))}
    </ul>
  )
}
