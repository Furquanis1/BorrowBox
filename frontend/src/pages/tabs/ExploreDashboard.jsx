import React from 'react'
import { useAuth } from '../../contexts/AuthContext'

export default function ExploreDashboard({ communityId }) {
  const { communities } = useAuth()
  const community = communities.find(c => c.id == communityId)

  return (
    <div className="explore-dashboard">
      {community ? (
        <>
          <div className="explore-community-header">
            <h2>{community.name}</h2>
            {community.description && <p>{community.description}</p>}
          </div>
          <div className="empty-state">
            <p>No items listed in this community yet. Asset management will be available in a future update.</p>
          </div>
        </>
      ) : (
        <div className="empty-state">
          <p>Select a community from the sidebar.</p>
        </div>
      )}
    </div>
  )
}
