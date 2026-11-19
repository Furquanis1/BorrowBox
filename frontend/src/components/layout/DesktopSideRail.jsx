import React, { useCallback, useEffect, useRef, useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'
import { useCommunity } from '../../contexts/CommunityContext'
import { useEvents } from '../../contexts/EventContext'
import Avatar from '../ui/Avatar'
import NotificationBell from '../ui/NotificationBell'
import CommunityLifecycleModal from '../community/CommunityLifecycleModal'
import EventPanel from './EventPanel'

const MEMBERSHIP_LABELS = {
  PENDING: 'Pending approval',
  SUSPENDED: 'Suspended',
  LEFT: 'Left',
  REJECTED: 'Not a member',
}

const PRIMARY_LINKS = [
  { to: '/dashboard', label: 'Dashboard', icon: 'bi-speedometer2', end: true },
  { to: '/me/inventory', label: 'Inventory', icon: 'bi-box' },
  { to: '/me/loans', label: 'Loans', icon: 'bi-arrow-left-right' },
  { to: '/me/requests', label: 'Requests', icon: 'bi-inbox' },
]

export default function DesktopSideRail() {
  const { user, signOut } = useAuth()
  const { communities, membershipById, rememberCommunity } = useCommunity()
  const { unreadCount } = useEvents()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuTab, setMenuTab] = useState('main')
  const [lifecycleOpen, setLifecycleOpen] = useState(false)
  const [lifecycleMode, setLifecycleMode] = useState('create')
  const menuRef = useRef(null)
  const eventWindowRef = useRef(null)

  const closeMenu = useCallback(() => {
    setMenuOpen(false)
    setMenuTab('main')
  }, [])

  const openLifecycle = (mode) => {
    setLifecycleMode(mode)
    setLifecycleOpen(true)
  }

  useEffect(() => {
    if (!menuOpen) return undefined
    const handleClickOutside = (event) => {
      const inMenu = menuRef.current && menuRef.current.contains(event.target)
      const inEventWindow = eventWindowRef.current && eventWindowRef.current.contains(event.target)
      if (!inMenu && !inEventWindow) closeMenu()
    }
    const handleEscape = (event) => {
      if (event.key === 'Escape') closeMenu()
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [menuOpen, closeMenu])

  const handleSignOut = async () => {
    closeMenu()
    await signOut()
    navigate('/')
  }

  return (
    <>
      <aside className="desktop-side-rail" aria-label="Primary navigation">
        <div className="rail-brand panel-brand">
          <span className="rail-brand-mark" aria-hidden="true">
            <i className="bi bi-box-seam" />
          </span>
          <span className="rail-brand-name">BorrowBox</span>
        </div>

        <div className="rail-scroll">
          <nav className="rail-section userbar-nav" aria-label="Workspace">
            <p className="rail-section-title">Workspace</p>
            {PRIMARY_LINKS.map(({ to, label, icon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                aria-label={label}
                className={({ isActive }) => `rail-link${isActive ? ' active' : ''}`}
              >
                <i className={`bi ${icon}`} aria-hidden="true" />
                <span>{label}</span>
              </NavLink>
            ))}
          </nav>

          <section className="rail-section rail-community-section community-panel" aria-label="Your Communities">
            <div className="rail-section-heading">
              <p className="rail-section-title">Your Communities</p>
            </div>
            {communities.length === 0 ? (
              <div className="rail-empty group-empty">
                <span>No communities yet.</span>
                <div className="rail-empty-actions">
                  <button
                    type="button"
                    className="rail-empty-action"
                    onClick={() => openLifecycle('create')}
                  >
                    Create a community
                  </button>
                  <button
                    type="button"
                    className="rail-empty-action"
                    onClick={() => openLifecycle('join')}
                  >
                    Join a community
                  </button>
                </div>
              </div>
            ) : (
              <div className="rail-community-list" role="group" aria-label="Your Communities">
                {communities.map((community) => {
                  const membership = membershipById[String(community.id)]
                  const statusNote = membership && membership.status !== 'ACTIVE'
                    ? `${MEMBERSHIP_LABELS[membership.status] || membership.status} · `
                    : ''

                  return (
                    <NavLink
                      key={community.id}
                      to={`/communities/${community.id}`}
                      className={({ isActive }) => `rail-link rail-community-link group-item${isActive ? ' active' : ''}`}
                      onClick={() => rememberCommunity(community.id)}
                      aria-label={`Enter ${community.name}`}
                    >
                      <span className="rail-community-dot" aria-hidden="true" />
                      <span className="rail-community-info">
                        <span className="rail-community-name">{community.name}</span>
                        <span className="rail-community-meta">
                          {statusNote}{community.membershipCount || 0} members
                        </span>
                      </span>
                    </NavLink>
                  )
                })}
              </div>
            )}
            <button
              type="button"
              className="rail-add-pill"
              onClick={() => openLifecycle('create')}
              aria-label="Add or join a community"
            >
              <i className="bi bi-plus" aria-hidden="true" />
              <span>Add or join</span>
            </button>
          </section>
        </div>

        <div className="rail-user-footer">
          <NotificationBell />
          <div className="avatar-menu rail-avatar-menu" ref={menuRef}>
            <button
              type="button"
              className="rail-user-button avatar-menu-btn"
              onClick={() => setMenuOpen((prev) => !prev)}
              aria-label="Account menu"
              aria-haspopup="menu"
              aria-expanded={menuOpen}
            >
              <span className="rail-profile-photo">
                <Avatar name={user?.fullName} size="sm" />
                {unreadCount > 0 && (
                  <span className="rail-profile-badge" aria-hidden="true">
                    {unreadCount > 9 ? '9+' : unreadCount}
                  </span>
                )}
              </span>
              <span className="rail-user-meta">
                <span className="rail-user-name">{user?.fullName || 'Account'}</span>
                <span className="rail-user-subtitle">Profile &amp; settings</span>
              </span>
              <i className={`bi bi-chevron-${menuOpen ? 'up' : 'down'}`} aria-hidden="true" />
            </button>

            {menuOpen && menuTab === 'main' && (
              <div className="avatar-menu-dropdown rail-user-dropdown" role="menu">
                <NavLink
                  to="/me/profile"
                  className="avatar-menu-item"
                  role="menuitem"
                  onClick={closeMenu}
                >
                  <i className="bi bi-person" aria-hidden="true" />
                  Profile
                </NavLink>
                <NavLink
                  to="/me/settings"
                  className="avatar-menu-item"
                  role="menuitem"
                  onClick={closeMenu}
                >
                  <i className="bi bi-gear" aria-hidden="true" />
                  Settings
                </NavLink>
                <button
                  type="button"
                  className="avatar-menu-item"
                  role="menuitem"
                  onClick={() => setMenuTab('events')}
                >
                  <i className="bi bi-bell" aria-hidden="true" />
                  Events
                  {unreadCount > 0 && <span className="avatar-menu-badge">{unreadCount}</span>}
                </button>
                <div className="avatar-menu-divider" role="separator" />
                <button
                  type="button"
                  className="avatar-menu-item"
                  role="menuitem"
                  onClick={handleSignOut}
                >
                  <i className="bi bi-box-arrow-right" aria-hidden="true" />
                  Sign Out
                </button>
              </div>
            )}
          </div>
        </div>
      </aside>

      {menuOpen && menuTab === 'events' && (
        <div className="event-window" ref={eventWindowRef} role="dialog" aria-label="Events">
          <EventPanel onClose={closeMenu} />
        </div>
      )}

      <CommunityLifecycleModal
        open={lifecycleOpen}
        initialMode={lifecycleMode}
        onClose={() => setLifecycleOpen(false)}
      />
    </>
  )
}
