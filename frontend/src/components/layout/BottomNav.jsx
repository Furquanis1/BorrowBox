import React from 'react'
import { NavLink } from 'react-router-dom'

const navItems = [
  { to: '/dashboard', label: 'Home', icon: 'bi-house' },
  { to: '/me/requests', label: 'Requests', icon: 'bi-inbox' },
  { to: '/me/inventory', label: 'Inventory', icon: 'bi-box' },
  { to: '/me/profile', label: 'Profile', icon: 'bi-person' },
]

export function BottomNav() {
  return (
    <nav className="floating-nav" aria-label="Primary navigation">
      {navItems.map(({ to, label, icon }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) => `nav-pill-item${isActive ? ' active' : ''}`}
        >
          <i className={`bi ${icon}`} aria-hidden="true" />
          <span className="visually-hidden">{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

export default BottomNav
