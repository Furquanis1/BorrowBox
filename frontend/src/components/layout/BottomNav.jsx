import React from 'react'
import { NavLink } from 'react-router-dom'

const PRIMARY_ITEMS = [
  { to: '/dashboard/explore', label: 'Explore', icon: 'bi-boxes' },
  { to: '/dashboard/inventory', label: 'Inventory', icon: 'bi-box' },
  { to: '/dashboard/requests', label: 'Requests', icon: 'bi-inbox' },
  { to: '/dashboard/loans', label: 'Loans', icon: 'bi-clipboard-check' },
]

export default function BottomNav({ onOpenMore }) {
  return (
    <nav className="bottom-nav" aria-label="Dashboard Navigation">
      {PRIMARY_ITEMS.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          className={({ isActive }) => `bottom-nav-item${isActive ? ' active' : ''}`}
        >
          <i className={`bi ${item.icon}`} aria-hidden="true" />
          <span>{item.label}</span>
        </NavLink>
      ))}
      <button
        type="button"
        className="bottom-nav-item"
        onClick={onOpenMore}
        aria-haspopup="dialog"
        aria-label="More options"
      >
        <i className="bi bi-grid-3x3-gap" aria-hidden="true" />
        <span>More</span>
      </button>
    </nav>
  )
}