import React, { useCallback, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'
import { useApp } from '../../contexts/AppContext'
import { useAsync } from '../../hooks/useAsync'
import { listingService, assetService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'
import ListingRowList from '../../components/dashboard/ListingRowList'
import RequestDrawer from '../../components/dashboard/RequestDrawer'
import CommunityBrowseControls from '../../components/common/CommunityBrowseControls'
import Pagination from '../../components/Pagination'
import {
  CATEGORY_FILTERS,
  matchesCategory,
  matchesSearchQuery,
} from '../../components/common/CategoryArtwork'

const PAGE_SIZE = 9

export default function ExplorePage() {
  const { communityId } = useParams()
  const { communities } = useCommunity()
  const { showToast, triggerRefresh } = useApp()
  const community = communities.find((c) => String(c.id) === String(communityId)) || null
  const [requestListing, setRequestListing] = useState(null)
  const [waitlistListing, setWaitlistListing] = useState(null)
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('All')
  const [page, setPage] = useState(0)

  const fetchListings = useCallback(() => {
    if (!community) return Promise.resolve([])
    return listingService.getCommunityListings(community.id)
  }, [community?.id])

  const fetchMyAssetIds = useCallback(async () => {
    const assets = await assetService.listAssets()
    return assets.map((asset) => Number(asset.id))
  }, [])

  const { data: listings, loading, error, reload, retry } = useAsync(fetchListings, [community?.id])
  const { data: myAssetIds } = useAsync(fetchMyAssetIds, [])

  const visibleListings = useMemo(
    () =>
      (listings || []).filter(
        (listing) => matchesSearchQuery(listing, query) && matchesCategory(listing, category)
      ),
    [listings, query, category]
  )

  const totalPages = Math.max(1, Math.ceil(visibleListings.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages - 1)
  const pagedListings = visibleListings.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE)

  if (!community) return null

  const handleRequested = async () => {
    setRequestListing(null)
    showToast('Request sent. The owner will see it in their lend requests.')
    triggerRefresh()
    try {
      await reload()
    } catch (err) {
      showToast(err?.message || 'Failed to refresh listings', 'error')
    }
  }

  const handleWaitlistJoined = async (joined) => {
    setWaitlistListing(null)
    showToast(
      `You are #${joined.position} in the waitlist. You'll be promoted when a unit is released.`
    )
    triggerRefresh()
    try {
      await reload()
    } catch (err) {
      showToast(err?.message || 'Failed to refresh listings', 'error')
    }
  }

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
            <Button variant="outline" onClick={retry}>
              <i className="bi bi-arrow-clockwise" aria-hidden="true" />
              Try again
            </Button>
          }
        />
      ) : (
        <section aria-label="Explore listing">
          <CommunityBrowseControls
            query={query}
            onQueryChange={(value) => {
              setQuery(value)
              setPage(0)
            }}
            category={category}
            onCategoryChange={(value) => {
              setCategory(value)
              setPage(0)
            }}
            categories={CATEGORY_FILTERS}
          />

          {listings.length === 0 ? (
            <EmptyState
              icon="bi-boxes"
              title="No items listed yet"
              description="Items offered in this community will show up here."
            />
          ) : visibleListings.length === 0 ? (
            <EmptyState
              icon="bi-search"
              title="No matching items"
              description="Try a different search or category."
            />
          ) : (
            <ListingRowList
              listings={pagedListings}
              myAssetIds={myAssetIds}
              onRequest={(listing) => setRequestListing(listing)}
              onJoinWaitlist={(listing) => setWaitlistListing(listing)}
            />
          )}

          {visibleListings.length > PAGE_SIZE && (
            <Pagination
              currentPage={safePage}
              totalPages={totalPages}
              onPageChange={setPage}
              totalElements={visibleListings.length}
              pageSize={PAGE_SIZE}
            />
          )}
        </section>
      )}

      <RequestDrawer
        open={!!requestListing}
        onClose={() => setRequestListing(null)}
        listing={requestListing}
        onSubmitted={handleRequested}
        mode="request"
      />

      <RequestDrawer
        open={!!waitlistListing}
        onClose={() => setWaitlistListing(null)}
        listing={waitlistListing}
        onSubmitted={handleWaitlistJoined}
        mode="waitlist"
      />
    </div>
  )
}
