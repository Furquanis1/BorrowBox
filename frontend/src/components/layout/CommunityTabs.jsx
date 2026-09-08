import React from 'react'
import { NavLink, useParams } from 'react-router-dom'

const TABS = [
  { segment: '', label: 'Home', icon: 'bi-house', end: true },
  { segment: 'explore', label: 'Explore', icon: 'bi-boxes' },
  { segment: 'members', label: 'Members', icon: 'bi-people' },
  { segment: 'rules', label: 'Rules', icon: 'bi-shield-check' },
]

export default function CommunityTabs() {
  const { communityId } = useParams()

  return (
    <nav className="community-tabs" aria-label="Community navigation">
      {TABS.map((tab) => (
        <NavLink
          key={tab.segment || 'home'}
          to={tab.segment
            ? `/communities/${communityId}/${tab.segment}`
            : `/communities/${communityId}`}
          end={tab.end}
          className={({ isActive }) => `community-tab${isActive ? ' active' : ''}`}
        >
          <i className={`bi ${tab.icon}`} aria-hidden="true" />
          {tab.label}
        </NavLink>
      ))}
    </nav>
  )
}
