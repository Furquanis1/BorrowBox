import React from 'react'
import Button from '../ui/Button'

export default function ListingRowList({ listings, myAssetIds = [], onRequest }) {
  return (
    <ul className="explore-listing-list">
      {listings.map((listing) => {
        const isMine = myAssetIds.includes(Number(listing.assetId))
        const canRequest = Boolean(onRequest) && !isMine && listing.availableUnits > 0
        return (
          <li key={listing.id} className="explore-listing-row">
            <div className="explore-listing-row-main">
              <h3>{listing.title}</h3>
              {listing.description && (
                <p className="explore-listing-description">{listing.description}</p>
              )}
            </div>
            <div className="explore-listing-row-meta">
              <span>{listing.availableUnits} available</span>
              <span>{listing.borrowedUnits} borrowed</span>
              <span>{listing.totalUnits} total</span>
              {canRequest && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => onRequest(listing)}
                  className="request-item-button"
                >
                  <i className="bi bi-send" aria-hidden="true" />
                  Request
                </Button>
              )}
            </div>
          </li>
        )
      })}
    </ul>
  )
}