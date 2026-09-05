import React from 'react'
import EmptyState from '../../components/ui/EmptyState'

export default function RulesPage() {
  return (
    <div className="rules-page">
      <section aria-label="Community rules">
        <EmptyState
          icon="bi-shield-check"
          title="Set the ground rules"
          description="Community rules will be available in a future update."
        />
      </section>
    </div>
  )
}