import React, { useCallback, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'
import { useCommunity } from '../../contexts/CommunityContext'
import { useApp } from '../../contexts/AppContext'
import { useAsync } from '../../hooks/useAsync'
import { flagService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'

const FLAG_TYPES = ['MANUAL', 'OVERDUE', 'HANDOVER_DISPUTED', 'RETURN_DISPUTED', 'EVIDENCE_ISSUE']
const FLAG_STATUSES = ['OPEN', 'REVIEWED', 'RESOLVED', 'DISMISSED']

function formatDateTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} ${d
    .toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`
}

function badgeTone(status) {
  if (status === 'OPEN') return 'badge-warning'
  if (status === 'RESOLVED') return 'badge-success'
  if (status === 'DISMISSED') return 'badge-neutral'
  return 'badge-info'
}

export default function FlagsPage() {
  const { communityId } = useParams()
  const { user } = useAuth()
  const { isManager } = useCommunity()
  const { showToast } = useApp()

  const [creating, setCreating] = useState(false)
  const [createFlagType, setCreateFlagType] = useState('MANUAL')
  const [createTransactionId, setCreateTransactionId] = useState('')
  const [createNote, setCreateNote] = useState('')
  const [savingFlag, setSavingFlag] = useState(false)
  const [actingFlagId, setActingFlagId] = useState(null)

  const [filters, setFilters] = useState({ status: '', flagType: '', transactionId: '' })
  const [appliedFilters, setAppliedFilters] = useState({})

  const fetchFlags = useCallback(() => {
    if (!communityId || !isManager(communityId)) return Promise.resolve([])
    return flagService.listFlags(communityId, {
      status: appliedFilters.status || undefined,
      flagType: appliedFilters.flagType || undefined,
      transactionId: appliedFilters.transactionId || undefined,
    })
  }, [communityId, appliedFilters, isManager])

  const { data: flags, loading, error, reload } = useAsync(fetchFlags, [communityId, appliedFilters])

  if (!isManager(communityId)) {
    return <Navigate to={`/communities/${communityId}`} replace />
  }

  const applyFilters = () => {
    setAppliedFilters({
      status: filters.status,
      flagType: filters.flagType,
      transactionId: filters.transactionId,
    })
  }

  const clearFilters = () => {
    setFilters({ status: '', flagType: '', transactionId: '' })
    setAppliedFilters({})
  }

  const handleCreateFlag = async (event) => {
    event.preventDefault()
    setSavingFlag(true)
    try {
      await flagService.createFlag(communityId, {
        flagType: createFlagType,
        transactionId: createTransactionId ? Number(createTransactionId) : null,
        note: createNote,
      })
      setCreating(false)
      setCreateTransactionId('')
      setCreateNote('')
      setCreateFlagType('MANUAL')
      showToast('Flag opened.')
      await reload()
    } catch (err) {
      showToast(err?.message || 'Failed to open the flag', 'error')
    } finally {
      setSavingFlag(false)
    }
  }

  const updateFlag = async (flagId, updates, successMessage) => {
    setActingFlagId(flagId)
    try {
      await flagService.updateFlag(communityId, flagId, updates)
      showToast(successMessage)
      await reload()
    } catch (err) {
      showToast(err?.message || 'Failed to update the flag', 'error')
    } finally {
      setActingFlagId(null)
    }
  }

  const changeStatus = (event, flagId) => {
    updateFlag(flagId, { status: event.target.value }, `Flag marked ${event.target.value}.`)
  }

  const assignToSelf = (flagId) => {
    updateFlag(flagId, { assigneeId: user.id }, 'Flag assigned to you.')
  }

  const unassign = (flagId) => {
    updateFlag(flagId, { clearAssignee: true }, 'Flag unassigned.')
  }

  return (
    <div className="flags-page">
      <section aria-labelledby="flags-title" className="flags-header">
        <h2 id="flags-title" className="manager-section-title">
          Flags
        </h2>
        <div className="flag-toolbar">
          <Button
            variant={creating ? 'outline' : 'primary'}
            onClick={() => setCreating((value) => !value)}
          >
            <i className="bi bi-flag" aria-hidden="true" />
            {creating ? 'Cancel' : 'Open a flag'}
          </Button>
        </div>
      </section>

      {creating && (
        <form className="flag-create-form" onSubmit={handleCreateFlag} aria-label="Open a flag">
          <div className="form-group">
            <label htmlFor="create-flag-type">Flag type</label>
            <select
              id="create-flag-type"
              className="select"
              value={createFlagType}
              onChange={(event) => setCreateFlagType(event.target.value)}
            >
              {FLAG_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="create-flag-transaction">Transaction ID (optional)</label>
            <input
              id="create-flag-transaction"
              className="input"
              type="text"
              inputMode="numeric"
              value={createTransactionId}
              onChange={(event) => setCreateTransactionId(event.target.value)}
              placeholder="e.g. 42"
            />
          </div>
          <div className="form-group">
            <label htmlFor="create-flag-note">Note (optional)</label>
            <textarea
              id="create-flag-note"
              className="textarea"
              value={createNote}
              onChange={(event) => setCreateNote(event.target.value)}
              placeholder="Describe the incident"
            />
          </div>
          <Button type="submit" loading={savingFlag}>
            Open flag
          </Button>
        </form>
      )}

      <section aria-labelledby="filter-title">
        <h3 id="filter-title" className="filter-title">
          Filter flags
        </h3>
        <div className="flag-filters">
          <div className="form-group">
            <label htmlFor="filter-flag-status">Status</label>
            <select
              id="filter-flag-status"
              className="select"
              value={filters.status}
              onChange={(event) => setFilters((prev) => ({ ...prev, status: event.target.value }))}
            >
              <option value="">All statuses</option>
              {FLAG_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="filter-flag-type">Type</label>
            <select
              id="filter-flag-type"
              className="select"
              value={filters.flagType}
              onChange={(event) => setFilters((prev) => ({ ...prev, flagType: event.target.value }))}
            >
              <option value="">All types</option>
              {FLAG_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="filter-flag-transaction">Transaction ID</label>
            <input
              id="filter-flag-transaction"
              className="input"
              type="text"
              inputMode="numeric"
              value={filters.transactionId}
              onChange={(event) =>
                setFilters((prev) => ({ ...prev, transactionId: event.target.value }))
              }
              placeholder="e.g. 42"
            />
          </div>
          <div className="flag-filter-actions">
            <Button variant="primary" size="sm" onClick={applyFilters}>
              Apply
            </Button>
            <Button variant="outline" size="sm" onClick={clearFilters}>
              Clear
            </Button>
          </div>
        </div>
      </section>

      {loading ? (
        <Spinner />
      ) : error ? (
        <EmptyState
          icon="bi-exclamation-triangle"
          title="Could not load flags"
          description={error.message}
          action={
            <Button variant="outline" onClick={reload}>
              <i className="bi bi-arrow-clockwise" aria-hidden="true" />
              Try again
            </Button>
          }
        />
      ) : flags.length === 0 ? (
        <EmptyState
          icon="bi-flag"
          title="No flags here"
          description="Flags raised in this community will show up here."
        />
      ) : (
        <ul className="flag-list">
          {flags.map((flag) => (
            <li key={flag.id} className="flag-item">
              <div className="flag-item-main">
                <div className="flag-item-head">
                  <span className={`badge ${badgeTone(flag.status)}`}>{flag.status}</span>
                  <span className="flag-item-type">{flag.flagType}</span>
                  <span className="flag-item-meta">
                    {flag.transactionId ? `Transaction #${flag.transactionId}` : 'No transaction'} ·{' '}
                    {flag.assigneeName ? `Assigned to ${flag.assigneeName}` : 'Unassigned'} ·{' '}
                    {formatDateTime(flag.occurredAt)}
                  </span>
                </div>
                {flag.note && <p className="flag-item-note">{flag.note}</p>}
                <div className="flag-item-actions">
                  <label>
                    <span className="flag-action-label">Status</span>
                    <select
                      className="select"
                      value={flag.status}
                      disabled={actingFlagId === flag.id}
                      onChange={(event) => changeStatus(event, flag.id)}
                    >
                      {FLAG_STATUSES.map((status) => (
                        <option key={status} value={status}>
                          {status}
                        </option>
                      ))}
                    </select>
                  </label>
                  {flag.assigneeId ? (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={actingFlagId === flag.id}
                      onClick={() => unassign(flag.id)}
                    >
                      Unassign
                    </Button>
                  ) : (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={actingFlagId === flag.id}
                      onClick={() => assignToSelf(flag.id)}
                    >
                      Assign to me
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}