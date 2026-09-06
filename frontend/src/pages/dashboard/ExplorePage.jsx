import React, { useCallback } from 'react'
import { useCommunity } from '../../contexts/CommunityContext'
import { useAsync } from '../../hooks/useAsync'
import { listingService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'

export default function ExplorePage() {
  const { communities, activeCommunity } = useCommunity()
  const community = communities.find((c) => c.id == activeCommunity)

  const fetchListings = useCallback(() => {
    if (!community) return Promise.resolve([])
    return listingService.getCommunityListings(community.id)
  }, [community?.id])

  const { data: listings, loading, error, reload } = useAsync(fetchListings, [community?.id])

  return (
    <div className="explore-dashboard">
      {community ? (
        <>
          <div className="explore-community-header">
            <h2>{community.name}</h2>
            {community.description && <p>{community.description}</p>}
          </div>

          {loading ? (
            <Spinner />
          ) : error ? (
            <EmptyState
              icon="bi-exclamation-triangle"
              title="Could not load listings"
              description={error.message}
              action={
                <Button variant="outline" onClick={reload}>
                  <i className="bi bi-arrow-clockwise" aria-hidden="true" />
                  Try again
                </Button>
              }
            />
          ) : listings.length === 0 ? (
            <section aria-label="Explore listing">
              <EmptyState
                icon="bi-boxes"
                title="No items listed yet"
                description="Assets listed in this community will show up here."
              />
            </section>
          ) : (
            <section aria-label="Explore listing">
              <ul className="explore-listing-grid">
                {listings.map((listing) => (
                  <li key={listing.id} className="explore-listing-card">
                    <h3>{listing.title}</h3>
                    {listing.description && <p className="explore-listing-description">{listing.description}</p>}
                    <div className="explore-listing-meta">
                      <span className="badge badge-success">{listing.availableUnits} available</span>
                      <span className="badge badge-warning">{listing.borrowedUnits} borrowed</span>
                      <span className="badge badge-neutral">{listing.totalUnits} total</span>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          )}
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