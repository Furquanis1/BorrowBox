import React, { useState, useRef, useEffect, useCallback } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'
import Avatar from '../ui/Avatar'

export default function UserBar() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef(null)
  const buttonRef = useRef(null)

  const closeMenu = useCallback(() => setMenuOpen(false), [])

  useEffect(() => {
    if (!menuOpen) return
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        closeMenu()
      }
    }
    const handleEscape = (e) => {
      if (e.key === 'Escape') closeMenu()
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
    <div className="userbar">
      <span className="userbar-brand">BorrowBox</span>

      <nav className="userbar-nav" aria-label="Personal navigation">
        <NavLink
          to="/me/inventory"
          className={({ isActive }) => `userbar-nav-item${isActive ? ' active' : ''}`}
        >
          <i className="bi bi-box" aria-hidden="true" />
          <span>Inventory</span>
        </NavLink>
        <NavLink
          to="/me/requests"
          className={({ isActive }) => `userbar-nav-item${isActive ? ' active' : ''}`}
        >
          <i className="bi bi-inbox" aria-hidden="true" />
          <span>Requests</span>
        </NavLink>
      </nav>

      <div className="userbar-actions">
        <div className="avatar-menu" ref={menuRef}>
          <button
            type="button"
            className="avatar-menu-btn"
            ref={buttonRef}
            onClick={() => setMenuOpen((prev) => !prev)}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
          >
            <Avatar name={user?.fullName} size="sm" />
            <span className="avatar-menu-name">{user?.fullName}</span>
            <i className={`bi bi-chevron-${menuOpen ? 'up' : 'down'}`} aria-hidden="true" />
          </button>
          {menuOpen && (
            <div className="avatar-menu-dropdown" role="menu">
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
    </div>
  )
}
