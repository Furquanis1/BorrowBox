import React from 'react'
import { Outlet } from 'react-router-dom'
import SkipLink from './SkipLink'
import DesktopSideRail from './DesktopSideRail'
import EventEnvelopeController from './EventEnvelope'

export default function AppShell() {
  return (
    <div className="desktop-shell">
      <SkipLink />
      <DesktopSideRail />

      <div className="desktop-main-area">
        <main
          id="main-content"
          className="desktop-canvas"
          aria-label="BorrowBox workspace"
          tabIndex={-1}
        >
          <Outlet />
        </main>
      </div>

      <EventEnvelopeController />
    </div>
  )
}
