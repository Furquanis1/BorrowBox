import React from 'react'
import EmptyState from '../../components/ui/EmptyState'
import NotificationPreferences from './NotificationPreferences'

export default function SettingsPage() {
  return (
    <div className="settings-page">
      <section aria-label="Settings">
        <h2>Settings</h2>
        <NotificationPreferences />
      </section>
    </div>
  )
}
