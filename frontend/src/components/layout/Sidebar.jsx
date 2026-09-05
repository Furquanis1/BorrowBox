import React from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'
import { useCommunity } from '../../contexts/CommunityContext'
import Avatar from '../ui/Avatar'
import Button from '../ui/Button'

const NAV_ITEMS = [
  { to: '/dashboard/explore', label: 'Explore', icon: 'bi-boxes' },
  { to: '/dashboard/inventory', label: 'Inventory', icon: 'bi-box' },
  { to: '/dashboard/requests', label: 'Requests', icon: 'bi-inbox' },
  { to: '/dashboard/loans', label: 'Loans', icon: 'bi-clipboard-check' },
  { to: '/dashboard/members', label: 'Members', icon: 'bi-people' },
  { to: '/dashboard/rules', label: 'Rules', icon: 'bi-shield-check' },
]

export default function Sidebar() {
  const { user, signOut } = useAuth()
  const { communities, activeCommunity, switchCommunity } = useCommunity()
  const navigate = useNavigate()

  const handleSignOut = async () => {
    await signOut()
    navigate('/')
  }

  const navItemClass = ({ isActive }) => `nav-item${isActive ? ' active' : ''}`

  return (
    <aside className="dashboard-sidebar" aria-label="Sidebar Navigation">
      <div className="sidebar-header">
        <span className="sidebar-logo" aria-hidden="true">
          <i className="bi bi-box-seam" />
        </span>
        <span className="sidebar-brand">BorrowBox</span>
      </div>

      <div className="sidebar-user-row">
        <Avatar name={user?.fullName} size="md" />
        <span className="sidebar-user">{user?.fullName}</span>
      </div>

      <div className="sidebar-section">
        <h4 className="sidebar-section-title">Communities</h4>
        {communities.length === 0 ? (
          <p className="group-empty">No communities yet.</p>
        ) : (
          <div className="group-list" role="group" aria-label="Communities">
            {communities.map((community) => (
              <button
                key={community.id}
                type="button"
                className={`group-item ${activeCommunity == community.id ? 'active' : ''}`}
                onClick={() => switchCommunity(community.id)}
                aria-pressed={activeCommunity == community.id}
              >
                <i className="bi bi-people group-icon" aria-hidden="true" />
                <span className="group-info">
                  <span className="group-name">{community.name}</span>
                  <span className="group-meta">{community.membershipCount || 0} members</span>
                </span>
              </button>
            ))}
          </div>
        )}
      </div>

      <nav className="sidebar-section" aria-label="Dashboard Navigation">
        <h4 className="sidebar-section-title">Navigation</h4>
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} className={navItemClass}>
            <i className={`bi ${item.icon}`} aria-hidden="true" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="sidebar-footer">
        <Button variant="ghost" block onClick={handleSignOut}>
          <i className="bi bi-box-arrow-right" aria-hidden="true" />
          Sign Out
        </Button>
      </div>
    </aside>
  )
}