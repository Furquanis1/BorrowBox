import React, { useCallback, useState } from 'react'
import { useAsync } from '../../hooks/useAsync'
import { useApp } from '../../contexts/AppContext'
import { useAuth } from '../../contexts/AuthContext'
import { requestService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'
import ConversationDrawer from '../../components/dashboard/ConversationDrawer'

const LOAN_STATES = new Set(['ACTIVE', 'RETURN_INITIATED', 'RETURN_REPORTED', 'COMPLETED'])

const STATE_BADGE = {
  ACTIVE: 'badge-teal',
  RETURN_INITIATED: 'badge-warning',
  RETURN_REPORTED: 'badge-info',
  COMPLETED: 'badge-neutral',
}

const STATE_LABEL = {
  ACTIVE: 'On loan',
  RETURN_INITIATED: 'Return in progress',
  RETURN_REPORTED: 'Return reported',
  COMPLETED: 'Completed',
}

const CONVERSATION_LABEL = {
  ACTIVE: 'Conversation',
  RETURN_INITIATED: 'Conversation',
  RETURN_REPORTED: 'Conversation',
  COMPLETED: 'View conversation',
}

function LoanCard({ loan, currentUserId, onConversation }) {
  const state = loan.state
  const isBorrower = currentUserId && loan.borrowerId === currentUserId
  const dueDate = new Date(loan.startedAt)
  dueDate.setDate(dueDate.getDate() + (loan.agreedDurationDays || 0))

  return (
    <li className="transaction-card">
      <div className="transaction-card-head">
        <div className="transaction-card-main">
          <h3>{loan.title}</h3>
          <p className="transaction-card-subline">
            {isBorrower
              ? `${loan.lenderName} · ${loan.communityName}`
              : `${loan.borrowerName} · ${loan.communityName}`}
          </p>
          <p className="transaction-card-terms">
            &ldquo;{loan.agreedPurpose || loan.purpose}&rdquo; · {loan.agreedDurationDays || loan.requestedDurationDays}{' '}
            days
          </p>
        </div>
        <span className={`badge ${STATE_BADGE[state] || 'badge-neutral'}`}>
          {STATE_LABEL[state] || state}
          <i className="bi bi-circle-fill transaction-reservation-dot" aria-hidden="true" />
        </span>
      </div>

      {state === 'COMPLETED' ? (
        <p className="transaction-card-note">
          Returned {new Date(loan.completedAt).toLocaleDateString()}.
        </p>
      ) : state === 'RETURN_REPORTED' ? (
        <p className="transaction-card-note">
          Handback reported. Awaiting the owner&apos;s receipt confirmation.
        </p>
      ) : state === 'RETURN_INITIATED' ? (
        <p className="transaction-card-note">
          Return in progress. Awaiting the handback report.
        </p>
      ) : (
        <p className="transaction-card-note">
          Started {new Date(loan.startedAt).toLocaleDateString()} · due around {dueDate.toLocaleDateString()}.
        </p>
      )}

      <div className="transaction-card-actions">
        <Button variant="outline" size="sm" onClick={() => onConversation()}>
          <i className="bi bi-chat-dots" aria-hidden="true" />
          {CONVERSATION_LABEL[state] || 'Conversation'}
        </Button>
      </div>
    </li>
  )
}

export default function LoansPage() {
  const { triggerRefresh } = useApp()
  const { user } = useAuth()
  const [conversationTarget, setConversationTarget] = useState(null)
  const fetchMine = useCallback(() => requestService.getMine(), [])
  const fetchLended = useCallback(() => requestService.getLendRequests(), [])
  const mineState = useAsync(fetchMine, [])
  const lendedState = useAsync(fetchLended, [])

  const loans = [...(mineState.data || []), ...(lendedState.data || [])].filter((t) =>
    LOAN_STATES.has(t.state)
  )

  const handleDataChanged = async () => {
    triggerRefresh()
    await Promise.allSettled([mineState.reload(), lendedState.reload()])
  }

  const loading = mineState.loading || lendedState.loading
  const loadError = mineState.error || lendedState.error

  if (loading && loans.length === 0) {
    return (
      <div className="active-loans">
        <Spinner />
      </div>
    )
  }

  if (loadError && loans.length === 0) {
    return (
      <div className="active-loans">
        <EmptyState
          icon="bi-exclamation-triangle"
          title="Could not load loans"
          description={loadError.message}
        />
      </div>
    )
  }

  return (
    <div className="active-loans">
      <header className="requests-header">
        <h2>My loans</h2>
        <p>Items you are borrowing, returning, or have returned.</p>
      </header>

      {loans.length === 0 ? (
        <EmptyState
          icon="bi-clipboard-check"
          title="No loans yet"
          description="Once an approved request moves through handover, your active loans will show up here."
        />
      ) : (
        <ul className="transaction-list">
          {loans.map((loan) => (
            <LoanCard
              key={loan.id}
              loan={loan}
              currentUserId={user?.id}
              onConversation={() => setConversationTarget(loan)}
            />
          ))}
        </ul>
      )}

      <ConversationDrawer
        open={!!conversationTarget}
        onClose={() => setConversationTarget(null)}
        transaction={conversationTarget}
        onDataChanged={handleDataChanged}
      />
    </div>
  )
}