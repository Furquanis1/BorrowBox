import React, { useState } from 'react'
import Drawer from '../ui/Drawer'
import Button from '../ui/Button'
import EmptyState from '../ui/EmptyState'
import { useCommunity } from '../../contexts/CommunityContext'
import { useApp } from '../../contexts/AppContext'
import { listingService } from '../../services'

/**
 * Drawer for managing where an owned asset is listed within the user's
 * communities. Mirrors the backend rules:
 *  - an owner can list in any community they are an ACTIVE member of,
 *  - listing again reactivates a previously unlisted (asset, community),
 *  - unlisting is a soft delete (LISTED -> UNLISTED).
 */
export default function ListAssetDrawer({ open, onClose, asset, listings = [], onMutated }) {
  const { communities, isActiveMember } = useCommunity()
  const { showToast } = useApp()
  const [busy, setBusy] = useState(null)

  if (!asset) return null

  const listed = listings.filter((l) => l.listingStatus === 'LISTED')
  const listedIds = new Set(listed.map((l) => String(l.communityId)))
  const eligible = communities
    .filter((c) => isActiveMember(c.id) && !listedIds.has(String(c.id)))
    .sort((a, b) => a.name.localeCompare(b.name))
  const notMember = communities
    .filter((c) => !isActiveMember(c.id))
    .sort((a, b) => a.name.localeCompare(b.name))

  const handleList = async (community) => {
    setBusy(`list-${community.id}`)
    try {
      await listingService.createListing(asset.id, { communityId: community.id })
      showToast(`"${asset.title}" is now listed in ${community.name}`)
      await onMutated?.()
    } catch (err) {
      showToast(err?.message || 'Failed to list the asset', 'error')
    } finally {
      setBusy(null)
    }
  }

  const handleUnlist = async (community) => {
    setBusy(`unlist-${community.id}`)
    try {
      await listingService.unlist(asset.id, community.id)
      showToast(`Listing removed from ${community.name}`)
      await onMutated?.()
    } catch (err) {
      showToast(err?.message || 'Failed to remove the listing', 'error')
    } finally {
      setBusy(null)
    }
  }

  return (
    <Drawer open={open} onClose={onClose} title={`List "${asset.title}"`} className="listing-drawer">
      <div className="listing-drawer-note">
        Where should this item be offered? Availability always comes from your
        own inventory.
      </div>

      {listed.length > 0 && (
        <section className="listing-drawer-section" aria-label="Currently listed">
          <h4 className="listing-drawer-section-title">Currently listed</h4>
          <ul className="listing-community-list">
            {listed.map((listing) => {
              const community = communities.find((c) => c.id == listing.communityId)
              return (
                <li key={listing.id} className="listing-community-row">
                  <div className="listing-community-info">
                    <span className="listing-community-name">{community?.name || `#${listing.communityId}`}</span>
                    <span className="badge badge-success">Listed</span>
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    loading={busy === `unlist-${listing.communityId}`}
                    onClick={() => handleUnlist(community)}
                  >
                    <i className="bi bi-slash-circle" aria-hidden="true" />
                    Unlist
                  </Button>
                </li>
              )
            })}
          </ul>
        </section>
      )}

      {eligible.length > 0 && (
        <section className="listing-drawer-section" aria-label="Eligible to list">
          <h4 className="listing-drawer-section-title">List in a community</h4>
          <ul className="listing-community-list">
            {eligible.map((community) => (
              <li key={community.id} className="listing-community-row">
                <div className="listing-community-info">
                  <span className="listing-community-name">{community.name}</span>
                  <span className="badge badge-neutral">Active member</span>
                </div>
                <Button
                  variant="primary"
                  size="sm"
                  loading={busy === `list-${community.id}`}
                  onClick={() => handleList(community)}
                >
                  <i className="bi bi-plus-lg" aria-hidden="true" />
                  List
                </Button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {eligible.length === 0 && listed.length > 0 && (
        <EmptyState
          icon="bi-check2-circle"
          title="Listed everywhere eligible"
          description="This asset is already listed in every community you can offer it in."
        />
      )}

      {notMember.length > 0 && (
        <section className="listing-drawer-section" aria-label="Not a member">
          <h4 className="listing-drawer-section-title">Join a community to list there</h4>
          <ul className="listing-community-list">
            {notMember.map((community) => (
              <li key={community.id} className="listing-community-row is-disabled">
                <div className="listing-community-info">
                  <span className="listing-community-name">{community.name}</span>
                  <span className="badge badge-neutral">Not a member</span>
                </div>
                <Button variant="ghost" size="sm" disabled>
                  List
                </Button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {eligible.length === 0 && listed.length === 0 && notMember.length === 0 && (
        <EmptyState
          icon="bi-people"
          title="No communities yet"
          description="Join a community before listing an item in it."
        />
      )}
    </Drawer>
  )
}