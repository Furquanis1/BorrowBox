import React from 'react'
import EmptyState from '../../components/ui/EmptyState'

export default function MembersPage() {
  return (
    <div className="members-page">
      <section aria-label="Community members">
        <EmptyState
          icon="bi-people"
          title="Meet your community"
          description="Community members will be available in a future update."
        />
      </section>
    </div>
  )
}