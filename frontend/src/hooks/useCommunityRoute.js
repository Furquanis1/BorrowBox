import { useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { useCommunity } from '../contexts/CommunityContext'

/**
 * Derives the displayed community from the URL. The :communityId route param
 * is the sole source of truth while inside Community Space; localStorage is
 * never consulted here, so a stale `lastCommunityId` cannot override an
 * explicit community in the URL.
 *
 * Returns:
 *  - paramId:   the :communityId from the URL (or null),
 *  - community: the matching community record from the user's memberships
 *               (null while loading, or when the param is missing/invalid).
 */
export function useCommunityRoute() {
  const params = useParams()
  const { communities } = useCommunity()

  const paramId = params.communityId != null ? String(params.communityId) : null

  const community = useMemo(
    () => communities.find((c) => String(c.id) === paramId) || null,
    [communities, paramId],
  )

  return { paramId, community }
}