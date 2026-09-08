import React from 'react'
import { Outlet } from 'react-router-dom'
import SkipLink from './SkipLink'
import CommunityPanel from './CommunityPanel'
import UserBar from './UserBar'

export default function AppShell() {
  return (
    <div className="dashboard app-shell">
      <SkipLink />
      <CommunityPanel />

      <div className="dashboard-main">
        <UserBar />
        <main id="main-content" className="dashboard-content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}