import React, { useCallback, useState } from 'react'
import { useAsync } from '../../hooks/useAsync'
import { useApp } from '../../contexts/AppContext'
import { assetService, listingService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'
import ListAssetDrawer from '../../components/dashboard/ListAssetDrawer'

export default function InventoryPage() {
  const { showToast } = useApp()
  const [drawerAsset, setDrawerAsset] = useState(null)

  const fetchAll = useCallback(async () => {
    const assets = await assetService.listAssets()
    const listingEntries = await Promise.all(
      assets.map((asset) => listingService.getAssetListings(asset.id)),
    )
    const listingsByAsset = {}
    assets.forEach((asset, index) => {
      listingsByAsset[asset.id] = listingEntries[index] || []
    })
    return { assets, listingsByAsset }
  }, [])

  const { data, loading, error, reload } = useAsync(fetchAll, [])

  const assets = data?.assets || []
  const listingsByAsset = data?.listingsByAsset || {}

  const handleMutated = async () => {
    try {
      await reload()
    } catch (err) {
      showToast(err?.message || 'Failed to refresh your inventory', 'error')
    }
  }

  if (loading && !data) {
    return (
      <div className="inventory-manager">
        <Spinner />
      </div>
    )
  }

  if (error && !data) {
    return (
      <div className="inventory-manager">
        <EmptyState
          icon="bi-exclamation-triangle"
          title="Could not load your inventory"
          description={error.message}
          action={
            <Button variant="outline" onClick={reload}>
              <i className="bi bi-arrow-clockwise" aria-hidden="true" />
              Try again
            </Button>
          }
        />
      </div>
    )
  }

  if (assets.length === 0) {
    return (
      <div className="inventory-manager">
        <header className="inventory-header">
          <h2>My Inventory</h2>
          <p>Everything you own, and the communities you share it with.</p>
        </header>
        <EmptyState
          icon="bi-box"
          title="Your inventory is empty"
          description="Add an item to start sharing it across your communities."
        />
      </div>
    )
  }

  return (
    <div className="inventory-manager">
      <header className="inventory-header">
        <h2>My Inventory</h2>
        <p>Everything you own, and the communities you share it with.</p>
      </header>

      <ul className="asset-list">
        {assets.map((asset) => {
          const assetListings = listingsByAsset[asset.id] || []
          const listedCount = assetListings.filter((l) => l.listingStatus === 'LISTED').length
          return (
            <li key={asset.id} className="asset-row">
              <div className="asset-row-main">
                <h3>{asset.title}</h3>
                {asset.description && <p>{asset.description}</p>}
                <div className="asset-row-badges">
                  <span className={`badge ${asset.status === 'ARCHIVED' ? 'badge-neutral' : 'badge-teal'}`}>
                    {asset.status}
                  </span>
                  <span className="badge badge-success">{asset.availableUnits} available</span>
                  <span className="badge badge-warning">{asset.borrowedUnits} borrowed</span>
                  <span className="badge badge-neutral">{asset.totalUnits} total</span>
                  <span className={`badge ${listedCount > 0 ? 'badge-info' : 'badge-neutral'}`}>
                    {listedCount > 0 ? `Listed in ${listedCount} community${listedCount > 1 ? 's' : ''}` : 'Not listed'}
                  </span>
                </div>
              </div>
              <div className="asset-row-actions">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={asset.status === 'ARCHIVED'}
                  onClick={() => setDrawerAsset(asset)}
                >
                  <i className="bi bi-plus-lg" aria-hidden="true" />
                  List in community
                </Button>
              </div>
            </li>
          )
        })}
      </ul>

      <ListAssetDrawer
        open={!!drawerAsset}
        onClose={() => setDrawerAsset(null)}
        asset={drawerAsset}
        listings={drawerAsset ? listingsByAsset[drawerAsset.id] : []}
        onMutated={handleMutated}
      />
    </div>
  )
}