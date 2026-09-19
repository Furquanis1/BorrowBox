import React from 'react'

function formatRate(rate) {
  if (rate === null || rate === undefined) return '—'
  return `${Math.round(rate * 100)}%`
}

function Stat({ icon, label, value, hint }) {
  return (
    <div className="trust-stat">
      <span className="trust-stat-icon" aria-hidden="true">
        <i className={`bi ${icon}`} />
      </span>
      <span className="trust-stat-value">{value}</span>
      <span className="trust-stat-label">{label}</span>
      {hint && <span className="trust-stat-hint">{hint}</span>}
    </div>
  )
}

export default function TrustSummary({ profile }) {
  if (!profile) return null

  const noCompletedLoans = !profile.completedLoans

  return (
    <section className="trust-summary" aria-label="Trust summary">
      <div className="requests-header">
        <h2>{profile.communityName ? `Trust in ${profile.communityName}` : 'Your trust profile'}</h2>
        <p>
          Derived automatically from your transaction history. There are no manual ratings.
        </p>
      </div>

      <div className="trust-stat-grid">
        <Stat icon="bi-box-arrow-in-down" label="Items borrowed" value={profile.itemsBorrowed} />
        <Stat icon="bi-box-arrow-up" label="Items lent" value={profile.itemsLent} />
        <Stat icon="bi-check2-circle" label="Successful transactions" value={profile.successfulTransactions} />
        <Stat icon="bi-journal-check" label="Completed loans" value={profile.completedLoans} />
        <Stat icon="bi-calendar-check" label="Returned on time" value={profile.onTimeReturns} />
        <Stat icon="bi-calendar-x" label="Returned late" value={profile.lateReturns} />
        <Stat
          icon="bi-speedometer2"
          label="On-time return rate"
          value={formatRate(profile.onTimeReturnRate)}
          hint={noCompletedLoans ? 'No completed loans yet' : undefined}
        />
      </div>
    </section>
  )
}
