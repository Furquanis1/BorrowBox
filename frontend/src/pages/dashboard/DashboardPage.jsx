import React, { useCallback, useMemo } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { useApp } from '../../contexts/AppContext'
import { useCommunity } from '../../contexts/CommunityContext'
import { useAsync } from '../../hooks/useAsync'
import { communityService, listingService, ruleService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'

function StatCard({ label, value, tone = 'default', className, icon, hint }) {
  return (
    <div className={`stat-card stat-${className}`.trim()} aria-label={`${label}: ${value}`}>
      <div className="stat-card-top">
        <span className={`stat-label stat-tone-${tone}`}>{label}</span>
        {icon && <i className={`bi ${icon}`} aria-hidden="true" />}
      </div>
      <span className="stat-value">{value}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </div>
  )
}

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString()
}

function formatDateTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} ${d
    .toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`
}

async function copyText(value) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!copied) throw new Error('Clipboard unavailable')
}

function percentage(part, total) {
  if (!total) return 0
  return Math.round((part / total) * 100)
}

export default function DashboardPage() {
  const { communityId } = useParams()
  const { isManager } = useCommunity()
  const { showToast } = useApp()

  const fetchDashboard = useCallback(() => {
    if (!communityId || !isManager(communityId)) return Promise.resolve(null)
    return communityService.getDashboard(communityId)
  }, [communityId, isManager])

  const fetchHealth = useCallback(() => {
    if (!communityId || !isManager(communityId)) return Promise.resolve(null)
    return communityService.getHealth(communityId)
  }, [communityId, isManager])

  const fetchCatalog = useCallback(() => {
    if (!communityId || !isManager(communityId)) return Promise.resolve([])
    return listingService.getCommunityListings(communityId).catch(() => [])
  }, [communityId, isManager])

  const fetchRules = useCallback(() => {
    if (!communityId || !isManager(communityId)) return Promise.resolve([])
    return ruleService.listActiveRules(communityId).catch(() => [])
  }, [communityId, isManager])

  const { data: dashboard, loading, error, retry } = useAsync(fetchDashboard, [communityId])
  const { data: health } = useAsync(fetchHealth, [communityId])
  const { data: listings } = useAsync(fetchCatalog, [communityId])
  const { data: activeRules } = useAsync(fetchRules, [communityId])

  const catalog = useMemo(() => (Array.isArray(listings) ? listings : []), [listings])
  const rules = useMemo(
    () => (Array.isArray(activeRules) ? activeRules.filter((rule) => rule.status === 'ACTIVE') : []),
    [activeRules],
  )
  const availableUnits = useMemo(
    () => catalog.reduce((sum, listing) => sum + (Number(listing.availableUnits) || 0), 0),
    [catalog],
  )
  const borrowedUnits = useMemo(
    () => catalog.reduce((sum, listing) => sum + (Number(listing.borrowedUnits) || 0), 0),
    [catalog],
  )
  const totalUnits = availableUnits + borrowedUnits
  const availablePercent = percentage(availableUnits, totalUnits)
  const borrowedPercent = percentage(borrowedUnits, totalUnits)

  const copyCommunityId = async () => {
    try {
      await copyText(String(communityId))
      showToast('Copied community ID', 'success')
    } catch (err) {
      showToast('Could not copy the community ID', 'danger')
    }
  }

  if (!isManager(communityId)) {
    return <Navigate to={`/communities/${communityId}`} replace />
  }

  if (loading) return <Spinner />

  if (error) {
    return (
      <EmptyState
        icon="bi-exclamation-triangle"
        title="Could not load the dashboard"
        description={error.message}
        action={
          <Button variant="outline" onClick={retry}>
            <i className="bi bi-arrow-clockwise" aria-hidden="true" />
            Try again
          </Button>
        }
      />
    )
  }

  if (!dashboard) {
    return (
      <EmptyState
        icon="bi-speedometer2"
        title="No dashboard data yet"
        description="The community dashboard is not available right now."
      />
    )
  }

  const pendingMembers = dashboard.pendingMembershipCount || 0
  const openFlags = dashboard.openFlagCount || 0
  const disputedTransactions = dashboard.returnDisputesCount || 0
  const needsMemberReview = pendingMembers > 0
  const needsFlagReview = openFlags > 0 || disputedTransactions > 0

  return (
    <div className="dashboard-page">
      <section aria-labelledby="manager-kpis-title" className="manager-kpi-section">
        <h2 id="manager-kpis-title" className="manager-section-title">
          <span id="overview-title">Manager Overview</span>
        </h2>
        <div className="manager-kpi-strip">
          <StatCard
            label="Active Members"
            value={dashboard.activeMemberCount}
            className="active-members"
            icon="bi-people"
          />
          <StatCard
            label="Pending Approvals"
            value={pendingMembers}
            className="pending-members"
            icon="bi-person-plus"
            hint="Join requests waiting on a manager"
          />
          <StatCard
            label="Active Loans"
            value={dashboard.activeLoanCount}
            className="active-loans"
            icon="bi-arrow-left-right"
          />
          <StatCard
            label="Open Flags & Disputes"
            value={openFlags + disputedTransactions}
            className="flags-disputes"
            icon="bi-exclamation-triangle"
            hint={`${openFlags} flags · ${disputedTransactions} disputes`}
          />
        </div>
      </section>

      <section aria-labelledby="action-needed-title" className="manager-action-section">
        <h2 id="action-needed-title" className="manager-section-title">
          Action Needed
        </h2>
        <div className="manager-action-grid">
          {needsMemberReview && (
            <article className="manager-action-card action-members">
              <span className="manager-action-icon" aria-hidden="true">
                <i className="bi bi-person-plus" />
              </span>
              <div className="manager-action-body">
                <strong>{pendingMembers} pending approval{pendingMembers === 1 ? '' : 's'}</strong>
                <p>New members are waiting for your decision before they can borrow.</p>
                <Link className="manager-action-link" to={`/communities/${communityId}/members`}>
                  Review pending members →
                </Link>
              </div>
            </article>
          )}
          {needsFlagReview && (
            <article className="manager-action-card action-flags">
              <span className="manager-action-icon" aria-hidden="true">
                <i className="bi bi-flag" />
              </span>
              <div className="manager-action-body">
                <strong>
                  {openFlags} open flag{openFlags === 1 ? '' : 's'} · {disputedTransactions} dispute
                  {disputedTransactions === 1 ? '' : 's'}
                </strong>
                <p>Community health needs a manager to review and resolve these reports.</p>
                <Link className="manager-action-link" to={`/communities/${communityId}/flags`}>
                  Resolve in Flags →
                </Link>
              </div>
            </article>
          )}
          {!needsMemberReview && !needsFlagReview && (
            <article className="manager-action-card action-clear">
              <span className="manager-action-icon" aria-hidden="true">
                <i className="bi bi-check2-circle" />
              </span>
              <div className="manager-action-body">
                <strong>All clear</strong>
                <p>No pending approvals, open flags, or disputed returns need attention.</p>
              </div>
            </article>
          )}
        </div>
      </section>

      <section aria-labelledby="performance-title">
        <h2 id="performance-title" className="manager-section-title">
          Loan &amp; Trust Performance
        </h2>
        <div className="stat-grid">
          <StatCard label="Overdue" value={dashboard.overdueLoanCount} className="overdue" tone="danger" icon="bi-exclamation-octagon" />
          <StatCard label="Open flags" value={dashboard.openFlagCount} className="open-flags" tone="warning" icon="bi-flag" />
          <StatCard label="Completed loans" value={dashboard.completedLoansCount} className="completed-loans" icon="bi-check2-circle" />
          <StatCard label="On-time rate" value={dashboard.onTimeReturnRate != null ? `${dashboard.onTimeReturnRate}%` : '—'} className="on-time-rate" tone="success" hint={`${dashboard.onTimeReturns} on time / ${dashboard.lateReturns} late`} />
          <StatCard label="Dispute rate" value={dashboard.disputeRate != null ? `${dashboard.disputeRate}%` : '—'} className="dispute-rate" tone="danger" hint={`${dashboard.returnDisputesCount} disputes`} />
          <StatCard label="Volume (30d)" value={dashboard.transactionVolume30d} className="volume-30d" icon="bi-graph-up-arrow" />
        </div>
      </section>

      <section aria-labelledby="community-health-title" className="community-health-card">
        <h2 id="community-health-title" className="manager-section-title">
          Community Health &amp; Utilization
        </h2>
        <div className="manager-utilization-grid">
          <article className="manager-utilization-card">
            <span className="manager-utilization-label">Catalog size</span>
            <strong>{catalog.length}</strong>
            <small>listings currently shared in this community</small>
          </article>
          <article className="manager-utilization-card">
            <span className="manager-utilization-label">Available vs borrowed</span>
            <strong>{availableUnits} / {borrowedUnits}</strong>
            <div className="manager-utilization-bar" aria-hidden="true">
              <span className="manager-utilization-available" style={{ width: `${availablePercent}%` }} />
              <span className="manager-utilization-borrowed" style={{ width: `${borrowedPercent}%` }} />
            </div>
            <small>{availablePercent}% available · {borrowedPercent}% borrowed</small>
          </article>
          <article className="manager-utilization-card">
            <span className="manager-utilization-label">Active rules</span>
            <strong>{rules.length}</strong>
            <Link className="manager-utilization-link" to={`/communities/${communityId}/rules`}>
              Manage rules →
            </Link>
          </article>
          <article className="manager-utilization-card manager-community-id-card">
            <span className="manager-utilization-label">Community ID</span>
            <strong>#{communityId}</strong>
            <small>Share this exact ID to invite trusted members.</small>
            <button type="button" className="manager-copy-id" onClick={copyCommunityId}>
              <i className="bi bi-copy" aria-hidden="true" />
              Copy ID
            </button>
          </article>
        </div>

        {health ? (
          <div className="stat-grid stat-grid-health">
            <StatCard label="Active members" value={health.activeMemberCount} className="active-members" />
            <StatCard label="Open flags" value={health.openFlagCount} className="open-flags" tone="warning" />
            <StatCard label="Overdue" value={health.overdueLoanCount} className="overdue" tone="danger" />
            <StatCard label="On-time rate" value={health.onTimeReturnRate != null ? `${health.onTimeReturnRate}%` : '—'} className="on-time-rate" tone="success" />
            <StatCard label="Dispute rate" value={health.disputeRate != null ? `${health.disputeRate}%` : '—'} className="dispute-rate" tone="danger" />
          </div>
        ) : (
          <p className="manager-section-empty">Health summary unavailable.</p>
        )}
      </section>

      <section aria-labelledby="recent-activity-title">
        <h2 id="recent-activity-title" className="manager-section-title">
          Recent activity
        </h2>
        {(dashboard.recentActivity || []).length === 0 ? (
          <p className="manager-section-empty">No activity recorded yet.</p>
        ) : (
          <ul className="activity-list">
            {dashboard.recentActivity.map((item) => (
              <li key={`${item.source}-${item.id}`} className="activity-item">
                <span className="activity-icon" aria-hidden="true">
                  <i className={`bi ${item.source === 'reputation' ? 'bi-stars' : 'bi-arrow-left-right'}`} />
                </span>
                <span className="activity-body">
                  <span className="activity-title">
                    {item.actorName || 'Someone'} · {item.eventType}
                  </span>
                  <span className="activity-meta">{formatDateTime(item.occurredAt)}</span>
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="recent-flags-title">
        <h2 id="recent-flags-title" className="manager-section-title">
          Recent flags
        </h2>
        {(dashboard.recentFlags || []).length === 0 ? (
          <p className="manager-section-empty">No open flags raised recently.</p>
        ) : (
          <ul className="activity-list">
            {dashboard.recentFlags.map((flag) => (
              <li key={flag.id} className="flag-chip">
                <span className={`badge badge-${flag.status === 'OPEN' ? 'warning' : 'neutral'}`}>
                  {flag.status}
                </span>
                <span className="flag-chip-type">{flag.flagType}</span>
                <span className="activity-meta">
                  {flag.assigneeName ? `Assigned to ${flag.assigneeName}` : 'Unassigned'} ·{' '}
                  {formatDate(flag.occurredAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
