import React from 'react'
import { NavLink, useParams } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'

const BASE_TABS = [
  { segment: '', label: 'Home', icon: 'bi-house', end: true },
  { segment: 'explore', label: 'Explore', icon: 'bi-boxes' },
  { segment: 'members', label: 'Members', icon: 'bi-people' },
  { segment: 'rules', label: 'Rules', icon: 'bi-shield-check' },
]

const MANAGER_TABS = [
  { segment: 'dashboard', label: 'Dashboard', icon: 'bi-speedometer2' },
  { segment: 'flags', label: 'Flags', icon: 'bi-flag' },
]

export default function CommunityTabs() {
  const { communityId } = useParams()
  const { isManager } = useCommunity()

  const tabs = isManager(communityId)
    ? [
        ...MANAGER_TABS.slice(0, 1),
        ...BASE_TABS.slice(0, 2),
        ...MANAGER_TABS.slice(1),
        ...BASE_TABS.slice(2),
      ]
    : BASE_TABS

  return (
    <nav className="community-tabs" aria-label="Community navigation">
      {tabs.map((tab) => (
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