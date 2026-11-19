import React from 'react'
import { NavLink, useParams } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'

const BASE_TABS = [
  { segment: '', label: 'Home', end: true },
  { segment: 'explore', label: 'Explore' },
  { segment: 'members', label: 'Members' },
  { segment: 'rules', label: 'Rules' },
]

const MANAGER_TABS = [
  { segment: 'dashboard', label: 'Dashboard' },
  { segment: 'flags', label: 'Flags' },
]

export default function CommunityTabs() {
  const { communityId } = useParams()
  const { isManager } = useCommunity()

  const tabs = isManager(communityId)
    ? [
        MANAGER_TABS[0],
        BASE_TABS[0],
        BASE_TABS[1],
        MANAGER_TABS[1],
        BASE_TABS[2],
        BASE_TABS[3],
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
          {tab.label}
        </NavLink>
      ))}
    </nav>
  )
}
