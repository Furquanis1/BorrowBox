import React, { useCallback, useState } from 'react'
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

export default function ExplorePage() {
  const { communityId } = useParams()
  const { communities } = useCommunity()
  const { showToast, triggerRefresh } = useApp()
  const community = communities.find((c) => String(c.id) === String(communityId)) || null
  const [requestListing, setRequestListing] = useState(null)

  const fetchListings = useCallback(() => {
    if (!community) return Promise.resolve([])
    return listingService.getCommunityListings(community.id)
  }, [community?.id])

  const fetchMyAssetIds = useCallback(async () => {
    const assets = await assetService.listAssets()
    return assets.map((asset) => Number(asset.id))
  }, [])

  const { data: listings, loading, error, reload } = useAsync(fetchListings, [community?.id])
  const { data: myAssetIds } = useAsync(fetchMyAssetIds, [])

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
          <ListingRowList
            listings={listings}
            myAssetIds={myAssetIds || []}
            onRequest={(listing) => setRequestListing(listing)}
          />
        </section>
      )}

      <RequestDrawer
        open={!!requestListing}
        onClose={() => setRequestListing(null)}
        listing={requestListing}
        onSubmitted={handleRequested}
      />
    </div>
  )
}