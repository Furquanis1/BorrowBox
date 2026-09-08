import React from 'react'
import { Navigate } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'

/**
 * Resolves a community-scoped target that has no :communityId in the URL
 * (post-login landing and /dashboard/* compatibility shims). Uses the
 * last-used community as the default; falls back to the first community;
 * users with no communities are sent to My Space (/me/inventory).
 */
export default function LandingRedirect({ segment }) {
  const { loaded, defaultCommunityId } = useCommunity()

  if (!loaded) return null
  if (!defaultCommunityId) return <Navigate to="/me/inventory" replace />
  return (
    <Navigate
      to={`/communities/${defaultCommunityId}${segment ? `/${segment}` : ''}`}
      replace
    />
  )
}