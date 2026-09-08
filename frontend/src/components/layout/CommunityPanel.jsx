import React from 'react'
import { NavLink } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'

export default function CommunityPanel() {
  const { communities, rememberCommunity } = useCommunity()

  return (
    <aside className="community-panel" aria-label="Your Communities">
      <div className="panel-brand">
        <span className="panel-logo" aria-hidden="true">
          <i className="bi bi-box-seam" />
        </span>
        <span className="panel-brand-name">BorrowBox</span>
      </div>

      <div className="panel-section">
        <h4 className="panel-section-title">Your Communities</h4>
        {communities.length === 0 ? (
          <p className="group-empty">No communities yet.</p>
        ) : (
          <div className="group-list" role="group" aria-label="Your Communities">
            {communities.map((community) => (
              <NavLink
                key={community.id}
                to={`/communities/${community.id}`}
                className={({ isActive }) => `group-item${isActive ? ' active' : ''}`}
                onClick={() => rememberCommunity(community.id)}
                aria-label={`Enter ${community.name}`}
              >
                <span className="group-info">
                  <span className="group-name">{community.name}</span>
                  <span className="group-meta">{community.membershipCount || 0} members</span>
                </span>
              </NavLink>
            ))}
          </div>
        )}
      </div>
    </aside>
  )
}