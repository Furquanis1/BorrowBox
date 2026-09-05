import React, { useState } from 'react'
import { Outlet, NavLink } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import BottomNav from './BottomNav'
import SkipLink from './SkipLink'
import Drawer from '../ui/Drawer'

export default function AppShell() {
  const [moreOpen, setMoreOpen] = useState(false)

  const closeMore = () => setMoreOpen(false)

  const drawerLinkClass = ({ isActive }) => `drawer-link${isActive ? ' active' : ''}`

  return (
    <div className="dashboard app-shell">
      <SkipLink />
      <Sidebar />

      <div className="dashboard-main">
        <Topbar onOpenMore={() => setMoreOpen(true)} />
        <main id="main-content" className="dashboard-content" tabIndex={-1}>
          <Outlet />
        </main>
        <BottomNav onOpenMore={() => setMoreOpen(true)} />
      </div>

      <Drawer
        open={moreOpen}
        onClose={closeMore}
        title="More"
        className="more-drawer"
        backdropClassName="more-drawer-backdrop"
      >
        <nav className="drawer-links" aria-label="More options">
          <NavLink to="/dashboard/members" className={drawerLinkClass} onClick={closeMore}>
            <i className="bi bi-people" aria-hidden="true" />
            Members
          </NavLink>
          <NavLink to="/dashboard/rules" className={drawerLinkClass} onClick={closeMore}>
            <i className="bi bi-shield-check" aria-hidden="true" />
            Rules
          </NavLink>
        </nav>
        <p className="more-drawer-note">More features coming soon.</p>
      </Drawer>
    </div>
  )
}