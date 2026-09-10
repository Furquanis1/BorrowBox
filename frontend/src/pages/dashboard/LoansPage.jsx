import React, { useCallback, useState } from 'react'
import { useAsync } from '../../hooks/useAsync'
import { useApp } from '../../contexts/AppContext'
import { requestService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'

const LOAN_STATES = new Set(['ACTIVE', 'RETURN_INITIATED', 'COMPLETED'])

const STATE_BADGE = {
  ACTIVE: 'badge-teal',
  RETURN_INITIATED: 'badge-warning',
  COMPLETED: 'badge-neutral',
}

const STATE_LABEL = {
  ACTIVE: 'On loan',
  RETURN_INITIATED: 'Return in progress',
  COMPLETED: 'Completed',
}

function LoanCard({ loan, busy, onInitiateReturn }) {
  const state = loan.state
  const dueDate = new Date(loan.startedAt)
  dueDate.setDate(dueDate.getDate() + (loan.agreedDurationDays || 0))

  return (
    <li className="transaction-card">
      <div className="transaction-card-head">
        <div className="transaction-card-main">
          <h3>{loan.title}</h3>
          <p className="transaction-card-subline">
            {loan.lenderName} · {loan.communityName}
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
      ) : state === 'RETURN_INITIATED' ? (
        <p className="transaction-card-note">
          Return reported. Awaiting the owner&apos;s receipt confirmation.
        </p>
      ) : (
        <p className="transaction-card-note">
          Started {new Date(loan.startedAt).toLocaleDateString()} · due around {dueDate.toLocaleDateString()}.
        </p>
      )}

      {state === 'ACTIVE' && (
        <div className="transaction-card-actions">
          <Button
            variant="primary"
            size="sm"
            loading={busy === 'initiateReturn'}
            onClick={() => onInitiateReturn()}
          >
            <i className="bi bi-arrow-90deg-left" aria-hidden="true" />
            I&apos;ve returned it
          </Button>
        </div>
      )}
    </li>
  )
}

export default function LoansPage() {
  const { showToast, triggerRefresh } = useApp()
  const [busy, setBusy] = useState(null)
  const fetchMine = useCallback(() => requestService.getMine(), [])
  const loansState = useAsync(fetchMine, [])
  const loans = (loansState.data || []).filter((t) => LOAN_STATES.has(t.state))

  const handleInitiateReturn = async (id) => {
    setBusy('initiateReturn')
    try {
      await requestService.initiateReturn(id)
      showToast('Return reported to the owner.')
      triggerRefresh()
      await loansState.reload()
    } catch (err) {
      showToast(err?.message || 'Action failed', 'error')
    } finally {
      setBusy(null)
    }
  }

  if (loansState.loading && loans.length === 0) {
    return (
      <div className="active-loans">
        <Spinner />
      </div>
    )
  }

  if (loansState.error && loans.length === 0) {
    return (
      <div className="active-loans">
        <EmptyState
          icon="bi-exclamation-triangle"
          title="Could not load loans"
          description={loansState.error.message}
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
              busy={busy}
              onInitiateReturn={() => handleInitiateReturn(loan.id)}
            />
          ))}
        </ul>
      )}
    </div>
  )
}