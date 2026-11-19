import React from 'react'
import CategoryArtwork from '../common/CategoryArtwork'

export default function ListingRowList({
  listings = [],
  myAssetIds,
  onRequest,
  onJoinWaitlist,
  onSelect,
  onAction,
  onBorrow,
  readOnly = false,
}) {
  const ownedAssetsKnown = Array.isArray(myAssetIds)

  return (
    <div className="immersive-feed">
      {listings.map((listing) => {
        const isMine = ownedAssetsKnown && myAssetIds.includes(Number(listing.assetId))
        const isAvailable = listing.availableUnits > 0

        const imageSrc =
          listing.imageUrl ||
          listing.image ||
          listing.photoUrl ||
          listing.assetImage ||
          listing.imagePath ||
          listing.asset?.imageUrl ||
          listing.asset?.image ||
          listing.asset?.photoUrl ||
          listing.asset?.imagePath ||
          null

        const title = listing.title || 'Untitled item'
        const category = listing.categoryName || listing.category || ''
        const owner =
          listing.ownerName ||
          listing.owner?.fullName ||
          listing.owner?.name ||
          listing.lenderName ||
          'Community'
        const availability = isAvailable
          ? `${listing.availableUnits} available`
          : listing.waitingCount > 0
            ? `${listing.waitingCount} waiting`
            : 'Waitlist open'

        const requestHandler = onRequest || onBorrow || onAction || onSelect
        const waitlistHandler = onJoinWaitlist || onAction || onBorrow || onSelect
        const canRequest = !readOnly && !isMine && isAvailable && Boolean(requestHandler)
        const canJoinWaitlist =
          !readOnly && ownedAssetsKnown && Boolean(onJoinWaitlist) && !isMine && !isAvailable
        const arrowHandler = canJoinWaitlist ? waitlistHandler : requestHandler

        return (
          <article key={listing.id} className="tb-card explore-listing-row">
            <div className="tb-card-media">
              {imageSrc ? (
                <img src={imageSrc} alt={`${title} preview`} />
              ) : (
                <CategoryArtwork title={title} category={category} />
              )}

              <span className={`tb-status-pill${isAvailable ? ' is-available' : ''}`}>
                {isAvailable ? 'Available' : 'Pre-loved'}
              </span>

              {!readOnly && (
                <button
                  type="button"
                  className="tb-save-button"
                  aria-label={`Save ${title}`}
                  title="Save"
                >
                  <i className="bi bi-heart" aria-hidden="true" />
                </button>
              )}
            </div>

            <div className="tb-card-body">
              <div className="tb-card-copy">
                <h3 className="tb-card-title">{title}</h3>
                <p className="tb-card-subtitle">
                  {owner} <span aria-hidden="true">•</span> {availability}
                </p>
              </div>

              {(canRequest || canJoinWaitlist) && (
                <button
                  type="button"
                  className={`tb-card-arrow${canJoinWaitlist ? ' join-waitlist-button' : ' request-item-button'}`}
                  onClick={arrowHandler ? () => arrowHandler(listing) : undefined}
                  aria-label={`${canJoinWaitlist ? 'Join waitlist for' : 'Open'} ${title}`}
                  title={canJoinWaitlist ? 'Join waitlist' : 'Open item'}
                >
                  <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                    <path
                      d="M5 12h13m-5-5 5 5-5 5"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </button>
              )}
            </div>
          </article>
        )
      })}
    </div>
  )
}
