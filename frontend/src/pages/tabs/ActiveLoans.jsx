import React from 'react'

export default function ActiveLoans({ userId }) {
  return (
    <div className="active-loans">
      <div className="empty-state">
        <p>No active loans. Loan tracking will be available in a future update.</p>
      </div>
    </div>
  )
}
