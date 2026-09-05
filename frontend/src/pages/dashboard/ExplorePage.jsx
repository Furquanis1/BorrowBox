import React from 'react'
import { useCommunity } from '../../contexts/CommunityContext'
import EmptyState from '../../components/ui/EmptyState'

export default function ExplorePage() {
  const { communities, activeCommunity } = useCommunity()
  const community = communities.find((c) => c.id == activeCommunity)

  return (
    <div className="explore-dashboard">
      {community ? (
        <>
          <div className="explore-community-header">
            <h2>{community.name}</h2>
            {community.description && <p>{community.description}</p>}
          </div>
          <section aria-label="Explore listing">
            <EmptyState
              icon="bi-boxes"
              title="No items listed yet"
              description="Asset management is coming soon. This community hasn't listed any shared items yet."
            />
          </section>
        </>
      ) : (
        <section aria-label="No community selected">
          <EmptyState
            icon="bi-people"
            title="Select a community"
            description="Choose a community from the sidebar to start exploring shared items."
          />
        </section>
      )}
    </div>
  )
}