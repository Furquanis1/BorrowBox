import React from 'react'
import { Outlet } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'
import SkipLink from './SkipLink'
import { BottomNav } from './BottomNav'
import EventEnvelopeController from './EventEnvelope'

export default function AppShell() {
  const { user } = useAuth()
  const displayName = user?.fullName || 'there'
  const initials = displayName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('') || 'BB'

  return (
    <div className="mobile-app-shell">
      <div className="mobile-app-container">
        <SkipLink />

        <header className="mobile-app-header">
          <div className="mobile-greeting">Hello, <strong>{displayName}</strong></div>

          <div className="mobile-header-actions">
            <button type="button" className="mobile-header-icon" aria-label="Search">
              <i className="bi bi-search" aria-hidden="true" />
            </button>
            <span className="mobile-avatar" aria-label="Your profile" title={displayName}>
              {initials}
            </span>
          </div>
        </header>

        <EventEnvelopeController />

        <main id="main-content" className="mobile-app-content" tabIndex={-1}>
          <Outlet />
        </main>

        <BottomNav />
      </div>
    </div>
  )
}
