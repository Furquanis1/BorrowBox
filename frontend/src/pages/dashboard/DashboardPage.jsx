import React, { useCallback } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'
import { useAsync } from '../../hooks/useAsync'
import { communityService } from '../../services'
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

export default function DashboardPage() {
  const { communityId } = useParams()
  const { isManager } = useCommunity()

  const fetchDashboard = useCallback(() => {
    if (!communityId || !isManager(communityId)) return Promise.resolve(null)
    return communityService.getDashboard(communityId)
  }, [communityId, isManager])

  const fetchHealth = useCallback(() => {
    if (!communityId || !isManager(communityId)) return Promise.resolve(null)
    return communityService.getHealth(communityId)
  }, [communityId, isManager])

  const { data: dashboard, loading, error, reload } = useAsync(fetchDashboard, [communityId])
  const { data: health } = useAsync(fetchHealth, [communityId])

  if (!isManager(communityId)) {
    return <Navigate to={`/communities/${communityId}`} replace />
  }

  return (
    <div className="dashboard-page">
      {loading ? (
        <Spinner />
      ) : error ? (
        <EmptyState
          icon="bi-exclamation-triangle"
          title="Could not load the dashboard"
          description={error.message}
          action={
            <Button variant="outline" onClick={reload}>
              <i className="bi bi-arrow-clockwise" aria-hidden="true" />
              Try again
            </Button>
          }
        />
      ) : !dashboard ? (
        <EmptyState
          icon="bi-speedometer2"
          title="No dashboard data yet"
          description="The community dashboard is not available right now."
        />
      ) : (
        <>
          <section aria-labelledby="overview-title">
            <h2 id="overview-title" className="manager-section-title">
              Overview
            </h2>
            <div className="stat-grid">
              <StatCard label="Active loans" value={dashboard.activeLoanCount} className="active-loans" icon="bi-arrow-left-right" />
              <StatCard label="Overdue" value={dashboard.overdueLoanCount} className="overdue" tone="danger" icon="bi-exclamation-octagon" />
              <StatCard label="Pending members" value={dashboard.pendingMembershipCount} className="pending-members" icon="bi-person-plus" />
              <StatCard label="Active members" value={dashboard.activeMemberCount} className="active-members" icon="bi-people" />
              <StatCard label="Open flags" value={dashboard.openFlagCount} className="open-flags" tone="warning" icon="bi-flag" />
              <StatCard label="Completed loans" value={dashboard.completedLoansCount} className="completed-loans" icon="bi-check2-circle" />
              <StatCard label="On-time rate" value={dashboard.onTimeReturnRate != null ? `${dashboard.onTimeReturnRate}%` : '—'} className="on-time-rate" tone="success" hint={`${dashboard.onTimeReturns} on time / ${dashboard.lateReturns} late`} />
              <StatCard label="Dispute rate" value={dashboard.disputeRate != null ? `${dashboard.disputeRate}%` : '—'} className="dispute-rate" tone="danger" hint={`${dashboard.returnDisputesCount} disputes`} />
              <StatCard label="Volume (30d)" value={dashboard.transactionVolume30d} className="volume-30d" icon="bi-graph-up-arrow" />
            </div>
          </section>

          <section aria-labelledby="community-health-title" className="community-health-card">
            <h2 id="community-health-title" className="manager-section-title">
              Community health
            </h2>
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
            {dashboard.recentActivity.length === 0 ? (
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
            {dashboard.recentFlags.length === 0 ? (
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
        </>
      )}
    </div>
  )
}