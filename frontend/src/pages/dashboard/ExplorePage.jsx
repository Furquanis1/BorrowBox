import React, { useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'
import { useAsync } from '../../hooks/useAsync'
import { listingService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'
import ListingRowList from '../../components/dashboard/ListingRowList'

export default function ExplorePage() {
  const { communityId } = useParams()
  const { communities } = useCommunity()
  const community = communities.find((c) => String(c.id) === String(communityId)) || null

  const fetchListings = useCallback(() => {
    if (!community) return Promise.resolve([])
    return listingService.getCommunityListings(community.id)
  }, [community?.id])

  const { data: listings, loading, error, reload } = useAsync(fetchListings, [community?.id])

  if (!community) return null

  return (
    <div className="explore-dashboard">
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
            description="Items offered in this community will show up here."
          />
        </section>
      ) : (
        <section aria-label="Explore listing">
          <ListingRowList listings={listings} />
        </section>
      )}
    </div>
  )
}