import React from 'react'
import EmptyState from '../../components/ui/EmptyState'

export default function SettingsPage() {
  return (
    <div className="settings-page">
      <section aria-label="Settings">
        <EmptyState
          icon="bi-gear"
          title="Settings"
          description="Settings and preferences are coming soon."
        />
      </section>
    </div>
  )
}