import React, { useCallback, useState } from 'react'
import { useApp } from '../../contexts/AppContext'
import { useAsync } from '../../hooks/useAsync'
import { requestService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'
import CounterOfferDrawer from '../../components/dashboard/CounterOfferDrawer'
import ConversationDrawer from '../../components/dashboard/ConversationDrawer'

const REQUEST_STATES = new Set(['PENDING', 'COUNTER_OFFERED', 'APPROVED', 'AWAITING_HANDOVER'])

const STATE_BADGE = {
  PENDING: 'badge-warning',
  APPROVED: 'badge-success',
  REJECTED: 'badge-neutral',
  COUNTER_OFFERED: 'badge-info',
  CANCELLED: 'badge-neutral',
  AWAITING_HANDOVER: 'badge-info',
  ACTIVE: 'badge-teal',
  RETURN_INITIATED: 'badge-warning',
  COMPLETED: 'badge-neutral',
}

const STATE_LABEL = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  COUNTER_OFFERED: 'Counter-offered',
  CANCELLED: 'Cancelled',
  AWAITING_HANDOVER: 'Awaiting handover',
  ACTIVE: 'On loan',
  RETURN_INITIATED: 'Return in progress',
  COMPLETED: 'Completed',
}

const CONVERSATION_LABEL = {
  PENDING: 'Conversation',
  COUNTER_OFFERED: 'Conversation',
  APPROVED: 'Discuss pickup',
  AWAITING_HANDOVER: 'Discuss pickup',
}

function TransactionCard({
  transaction,
  role,
  busy,
  onApprove,
  onReject,
  onCounterOffer,
  onAcceptCounter,
  onCancel,
  onStageHandover,
  onConfirmHandover,
  onConversation,
}) {
  const state = transaction.state
  const isMine = role === 'mine'
  const agreed = ['APPROVED', 'AWAITING_HANDOVER', 'ACTIVE', 'RETURN_INITIATED'].includes(state)
  const reservationHint = transaction.reservationHeld
    ? state === 'ACTIVE' || state === 'RETURN_INITIATED'
      ? 'A unit is with the borrower.'
      : state === 'APPROVED'
        ? 'A unit is reserved for you.'
        : 'A unit is reserved while the request is open.'
    : null

  return (
    <li className="transaction-card">
      <div className="transaction-card-head">
        <div className="transaction-card-main">
          <h3>{transaction.title}</h3>
          <p className="transaction-card-subline">
            {isMine
              ? `${transaction.lenderName} · ${transaction.communityName}`
              : `${transaction.borrowerName} · ${transaction.communityName}`}
          </p>
          <p className="transaction-card-terms">
            &ldquo;{agreed ? transaction.agreedPurpose : transaction.purpose}&rdquo;
            {' · '}
            {agreed
              ? `${transaction.agreedDurationDays} days`
              : `${transaction.requestedDurationDays} days requested`}
          </p>
        </div>
        <span className={`badge ${STATE_BADGE[state] || 'badge-neutral'}`}>
          {STATE_LABEL[state] || state}
          <i className="bi bi-circle-fill transaction-reservation-dot" aria-hidden="true" />
        </span>
      </div>

      {transaction.decisionNote && (
        <p className="transaction-card-note">{transaction.decisionNote}</p>
      )}

      {state === 'COUNTER_OFFERED' && (
        <p className="transaction-card-counter">
          The owner offered {transaction.counterDurationDays} days
          {transaction.counterNote ? ` — "${transaction.counterNote}"` : ''}.{' '}
          {isMine ? 'You can accept it or cancel your request.' : 'Waiting for the borrower to reply.'}
        </p>
      )}

      {(state === 'APPROVED' || state === 'AWAITING_HANDOVER') && (
        <p className="transaction-card-note">
          Agreed for {transaction.agreedDurationDays} days (from{' '}
          {new Date(transaction.agreedAt).toLocaleDateString()}).
        </p>
      )}

      {(state === 'ACTIVE' || state === 'RETURN_INITIATED') && (
        <p className="transaction-card-note">
          Loan started {new Date(transaction.startedAt).toLocaleDateString()} · agreed for{' '}
          {transaction.agreedDurationDays} days.
        </p>
      )}

      {state === 'COMPLETED' && (
        <p className="transaction-card-note">
          Completed {new Date(transaction.completedAt).toLocaleDateString()}.
        </p>
      )}

      {reservationHint && <p className="transaction-card-reservation">{reservationHint}</p>}

      <div className="transaction-card-actions">
        <Button variant="outline" size="sm" onClick={() => onConversation()}>
          <i className="bi bi-chat-dots" aria-hidden="true" />
          {CONVERSATION_LABEL[state] || 'Conversation'}
        </Button>

        {!isMine && state === 'PENDING' && (
          <>
            <Button variant="primary" size="sm" loading={busy === 'approve'} onClick={() => onApprove()}>
              <i className="bi bi-check-lg" aria-hidden="true" />
              Approve
            </Button>
            <Button variant="outline" size="sm" loading={busy === 'counter'} onClick={() => onCounterOffer()}>
              <i className="bi bi-arrow-repeat" aria-hidden="true" />
              Counter-offer
            </Button>
            <Button variant="outline" size="sm" loading={busy === 'reject'} onClick={() => onReject()}>
              <i className="bi bi-x-lg" aria-hidden="true" />
              Reject
            </Button>
          </>
        )}

        {isMine && state === 'COUNTER_OFFERED' && (
          <>
            <Button variant="primary" size="sm" loading={busy === 'accept'} onClick={() => onAcceptCounter()}>
              <i className="bi bi-check-lg" aria-hidden="true" />
              Accept counter-offer
            </Button>
            <Button variant="outline" size="sm" loading={busy === 'cancel'} onClick={() => onCancel()}>
              <i className="bi bi-x-lg" aria-hidden="true" />
              Cancel request
            </Button>
          </>
        )}

        {isMine && state === 'PENDING' && (
          <Button variant="outline" size="sm" loading={busy === 'cancel'} onClick={() => onCancel()}>
            <i className="bi bi-x-lg" aria-hidden="true" />
            Cancel request
          </Button>
        )}

        {state === 'APPROVED' && (
          <Button variant="primary" size="sm" loading={busy === 'stage'} onClick={() => onStageHandover()}>
            <i className="bi bi-hand-index-thumb" aria-hidden="true" />
            Schedule handover
          </Button>
        )}

        {state === 'AWAITING_HANDOVER' && (
          <>
            {!isMine && (
              <Button
                variant="primary"
                size="sm"
                loading={busy === 'confirmHandover'}
                onClick={() => onConfirmHandover()}
              >
                <i className="bi bi-box-arrow-down" aria-hidden="true" />
                Confirm handover
              </Button>
            )}
            {!isMine ? (
              <Button variant="outline" size="sm" loading={busy === 'cancel'} onClick={() => onCancel()}>
                <i className="bi bi-x-lg" aria-hidden="true" />
                Cancel
              </Button>
            ) : (
              <Button variant="outline" size="sm" loading={busy === 'cancel'} onClick={() => onCancel()}>
                <i className="bi bi-x-lg" aria-hidden="true" />
                Cancel booking
              </Button>
            )}
          </>
        )}
      </div>
    </li>
  )
}

const EmptyRequests = ({ title, description, icon }) => (
  <EmptyState icon={icon} title={title} description={description} />
)

export default function RequestsPage() {
  const { showToast, triggerRefresh } = useApp()
  const [tab, setTab] = useState('incoming')
  const [busy, setBusy] = useState(null)
  const [counterTarget, setCounterTarget] = useState(null)
  const [conversationTarget, setConversationTarget] = useState(null)

  const fetchIncoming = useCallback(() => requestService.getLendRequests(), [])
  const fetchMine = useCallback(() => requestService.getMine(), [])

  const incomingState = useAsync(fetchIncoming, [])
  const mineState = useAsync(fetchMine, [])

  const refreshAll = async () => {
    triggerRefresh()
    await Promise.allSettled([incomingState.reload(), mineState.reload()])
  }

  const run = async (task, successMessage) => {
    setBusy(task.key)
    try {
      await task.run()
      showToast(successMessage)
      await refreshAll()
    } catch (err) {
      showToast(err?.message || 'Action failed', 'error')
    } finally {
      setBusy(null)
    }
  }

  const incoming = (incomingState.data || []).filter((t) => REQUEST_STATES.has(t.state))
  const mine = (mineState.data || []).filter((t) => REQUEST_STATES.has(t.state))
  const loading = incomingState.loading || mineState.loading
  const loadError = incomingState.error || mineState.error

  const requestCards = tab === 'incoming' ? incoming : mine
  const role = tab === 'incoming' ? 'lender' : 'mine'

  const handleCounterSubmitted = async () => {
    setCounterTarget(null)
    showToast('Counter-offer sent to the borrower.')
    await refreshAll()
  }

  if (loading && requestCards.length === 0) {
    return (
      <div className="requests-page request-inbox">
        <Spinner />
      </div>
    )
  }

  if (loadError && requestCards.length === 0) {
    return (
      <div className="requests-page request-inbox">
        <EmptyRequests
          icon="bi-exclamation-triangle"
          title="Could not load requests"
          description={loadError.message}
        />
      </div>
    )
  }

  return (
    <div className="requests-page request-inbox">
      <header className="requests-header">
        <h2>Requests</h2>
        <p>Negotiate a borrow with the people around you.</p>
      </header>

      <div className="requests-tabs" role="tablist" aria-label="Request view">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'incoming'}
          className={`requests-tab${tab === 'incoming' ? ' active' : ''}`}
          onClick={() => setTab('incoming')}
        >
          Incoming
          <span className="requests-tab-count">{incoming.length}</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'mine'}
          className={`requests-tab${tab === 'mine' ? ' active' : ''}`}
          onClick={() => setTab('mine')}
        >
          My requests
          <span className="requests-tab-count">{mine.length}</span>
        </button>
      </div>

      {requestCards.length === 0 ? (
        tab === 'incoming' ? (
          <EmptyRequests
            icon="bi-inbox"
            title="No incoming requests"
            description="Borrow requests for your assets will show up here."
          />
        ) : (
          <EmptyRequests
            icon="bi-send"
            title="No requests yet"
            description="Ask for a listed item from a community Explore page."
          />
        )
      ) : (
        <ul className="transaction-list">
          {requestCards.map((transaction) => (
            <TransactionCard
              key={transaction.id}
              transaction={transaction}
              role={role}
              busy={busy}
              onApprove={() =>
                run({ key: 'approve', run: () => requestService.approve(transaction.id) }, 'Request approved')
              }
              onReject={() =>
                run({ key: 'reject', run: () => requestService.reject(transaction.id) }, 'Request rejected')
              }
              onCounterOffer={() => setCounterTarget(transaction)}
              onAcceptCounter={() =>
                run({ key: 'accept', run: () => requestService.acceptCounter(transaction.id) }, 'Counter-offer accepted')
              }
              onCancel={() =>
                run({ key: 'cancel', run: () => requestService.cancel(transaction.id) }, 'Request cancelled')
              }
              onStageHandover={() =>
                run({ key: 'stage', run: () => requestService.stageHandover(transaction.id) }, 'Handover scheduled')
              }
              onConfirmHandover={() =>
                run({ key: 'confirmHandover', run: () => requestService.confirmHandover(transaction.id) }, 'Loan started')
              }
              onConversation={() => setConversationTarget(transaction)}
            />
          ))}
        </ul>
      )}

      <CounterOfferDrawer
        open={!!counterTarget}
        onClose={() => setCounterTarget(null)}
        transaction={counterTarget}
        onSubmitted={handleCounterSubmitted}
      />

      <ConversationDrawer
        open={!!conversationTarget}
        onClose={() => setConversationTarget(null)}
        transaction={conversationTarget}
        onDataChanged={refreshAll}
      />
    </div>
  )
}