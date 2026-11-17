import React from 'react'
import Button from '../ui/Button'

export default function ListingRowList({ listings, myAssetIds, onRequest, onJoinWaitlist }) {
  const ownedAssetsKnown = Array.isArray(myAssetIds)

  return (
    <div className="immersive-feed">
      {listings.map((listing) => {
        const isMine = ownedAssetsKnown && myAssetIds.includes(Number(listing.assetId))
        const canRequest =
          ownedAssetsKnown && Boolean(onRequest) && !isMine && listing.availableUnits > 0
        const canJoinWaitlist =
          ownedAssetsKnown && Boolean(onJoinWaitlist) && !isMine && listing.availableUnits === 0

        const imageSrc =
          listing.imageUrl ||
          listing.image ||
          listing.photoUrl ||
          listing.assetImage ||
          listing.asset?.imageUrl ||
          listing.asset?.image ||
          null
        const condition = listing.condition || listing.itemCondition || 'Pre-loved'
        const availability =
          listing.availableUnits > 0
            ? `${listing.availableUnits} available`
            : listing.waitingCount > 0
              ? `${listing.waitingCount} waiting`
              : 'Waitlist open'

        return (
          <article key={listing.id} className="immersive-card">
            {imageSrc ? (
              <img
                className="immersive-card-bg"
                src={imageSrc}
                alt={listing.title ? `${listing.title} preview` : 'Item preview'}
              />
            ) : (
              <div className="immersive-card-bg" aria-hidden="true" />
            )}

            <div className="immersive-card-overlay">
              <div className="immersive-card-content">
                <h3 className="immersive-card-title">{listing.title}</h3>

                <div className="immersive-card-meta">
                  <span>
                    <i className="bi bi-stars" aria-hidden="true" />
                    {condition}
                  </span>
                  <span>
                    <i className="bi bi-box-seam" aria-hidden="true" />
                    {availability}
                  </span>
                  {listing.waitingCount > 0 && (
                    <span className="badge badge-info">{listing.waitingCount} waiting</span>
                  )}
                </div>

                {canRequest && (
                  <Button
                    variant="primary"
                    onClick={() => onRequest(listing)}
                    className="request-item-button"
                  >
                    <i className="bi bi-send" aria-hidden="true" />
                    Request
                  </Button>
                )}
                {canJoinWaitlist && (
                  <Button
                    variant="primary"
                    onClick={() => onJoinWaitlist(listing)}
                    className="join-waitlist-button"
                  >
                    <i className="bi bi-clock-history" aria-hidden="true" />
                    Join waitlist
                  </Button>
                )}
              </div>
            </div>
          </article>
        )
      })}
    </div>
  )
}
