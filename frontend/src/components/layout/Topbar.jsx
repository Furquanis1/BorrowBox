import React from 'react'
import { useLocation } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'

const PAGE_TITLES = {
  explore: 'Explore Items',
  inventory: 'My Inventory',
  requests: 'Borrow Requests',
  loans: 'Active Loans',
  members: 'Members',
  rules: 'Community Rules',
}

export default function Topbar({ onOpenMore }) {
  const location = useLocation()
  const { communities, activeCommunity } = useCommunity()

  const segment = location.pathname.split('/')[2] || 'explore'
  const title = PAGE_TITLES[segment] || 'Explore Items'
  const currentCommunity = communities.find((c) => c.id == activeCommunity)

  return (
    <div className="dashboard-topbar">
      <div className="topbar-left">
        <h1 className="topbar-title">{title}</h1>
        {currentCommunity && (
          <p className="topbar-subtitle">
            in <strong>{currentCommunity.name}</strong>
          </p>
        )}
      </div>
      <button
        type="button"
        className="topbar-more"
        onClick={onOpenMore}
        aria-haspopup="dialog"
        aria-label="More options"
      >
        <i className="bi bi-list" aria-hidden="true" />
        More
      </button>
    </div>
  )
}