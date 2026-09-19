import React from 'react'
import Button from '../ui/Button'

export default function ListingRowList({ listings, myAssetIds, onRequest, onJoinWaitlist }) {
  const ownedAssetsKnown = Array.isArray(myAssetIds)
  return (
    <ul className="explore-listing-list">
      {listings.map((listing) => {
        const isMine = ownedAssetsKnown && myAssetIds.includes(Number(listing.assetId))
        const canRequest =
          ownedAssetsKnown && Boolean(onRequest) && !isMine && listing.availableUnits > 0
        const canJoinWaitlist =
          ownedAssetsKnown && Boolean(onJoinWaitlist) && !isMine && listing.availableUnits === 0
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
              {listing.waitingCount > 0 && (
                <span className="badge badge-info">{listing.waitingCount} waiting</span>
              )}
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
              {canJoinWaitlist && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => onJoinWaitlist(listing)}
                  className="join-waitlist-button"
                >
                  <i className="bi bi-clock-history" aria-hidden="true" />
                  Join waitlist
                </Button>
              )}
            </div>
          </li>
        )
      })}
    </ul>
  )
}
