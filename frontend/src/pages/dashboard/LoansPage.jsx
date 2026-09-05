import React from 'react'
import EmptyState from '../../components/ui/EmptyState'

export default function LoansPage() {
  return (
    <div className="active-loans">
      <section aria-label="Active loans">
        <EmptyState
          icon="bi-clipboard-check"
          title="No active loans"
          description="Loan tracking is coming soon. Active loans will be listed here."
        />
      </section>
    </div>
  )
}