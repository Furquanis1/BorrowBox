import React from 'react'
import EmptyState from '../../components/ui/EmptyState'

export default function RequestsPage() {
  return (
    <div className="request-inbox">
      <section aria-label="Borrow requests">
        <EmptyState
          icon="bi-inbox"
          title="No pending requests"
          description="Request management is coming soon. Borrow requests will show up here."
        />
      </section>
    </div>
  )
}