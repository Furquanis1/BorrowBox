import React, { useCallback } from 'react'
import { useCommunityRoute } from '../../hooks/useCommunityRoute'
import { useAsync } from '../../hooks/useAsync'
import { listingService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'
import ListingRowList from '../../components/dashboard/ListingRowList'

export default function CommunityHomePage() {
  const { community } = useCommunityRoute()

  const fetchListings = useCallback(() => {
    if (!community) return Promise.resolve([])
    return listingService.getCommunityListings(community.id)
  }, [community?.id])

  const { data: listings, loading, error, reload } = useAsync(fetchListings, [community?.id])

  if (!community) return null

  return (
    <div className="community-home">
      <section aria-labelledby="recently-offered-title">
        <h2 id="recently-offered-title" className="community-home-section-title">
          Recently offered
        </h2>
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
          <EmptyState
            icon="bi-boxes"
            title="Nothing shared yet"
            description="Items offered in this community will show up here."
          />
        ) : (
          <ListingRowList listings={listings.slice(0, 6)} />
        )}
      </section>
    </div>
  )
}