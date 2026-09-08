import React from 'react'
import { Navigate } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'
import { useCommunityRoute } from '../../hooks/useCommunityRoute'

/**
 * Guard for /communities/:communityId/*. The URL is authoritative: if the
 * explicit :communityId does not resolve to one of the user's communities,
 * redirect to the default community (or My Space when the user has none).
 * Never falls back to localStorage while a community is present in the URL.
 */
export default function CommunityRouteGuard({ children }) {
  const { loaded, defaultCommunityId } = useCommunity()
  const { community } = useCommunityRoute()

  if (!loaded) return null
  if (community) return children
  return <Navigate to={defaultCommunityId ? `/communities/${defaultCommunityId}` : '/me/inventory'} replace />
}