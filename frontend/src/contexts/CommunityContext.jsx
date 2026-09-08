import React, { createContext, useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from './AuthContext'
import { communityService, membershipService } from '../services'

export const CommunityContext = createContext()

/**
 * Owns the signed-in user's community data:
 *  - the list of communities they belong to (any membership status),
 *  - their membership record for each community (role + status).
 *
 * It does NOT hold a "current/active community". The URL is the sole source
 * of truth for the displayed community while inside Community Space
 * (/communities/:communityId). localStorage may remember `lastCommunityId`
 * only as a next-login/default fallback when no community is in the URL.
 */
export function CommunityProvider({ children }) {
  const { user } = useAuth()
  const [communities, setCommunities] = useState([])
  const [memberships, setMemberships] = useState([])
  const [membershipById, setMembershipById] = useState({})
  const [loading, setLoading] = useState(false)
  // True only after a load attempt has finished for the CURRENT user. Redirects
  // must wait on this (not `loading`) so a fresh page load on a protected route
  // never resolves an empty community list before the fetch completes.
  const [loaded, setLoaded] = useState(false)

  const applyData = (communityList, membershipList) => {
    const byId = {}
    for (const m of membershipList) {
      byId[String(m.communityId)] = m
    }
    setCommunities(communityList)
    setMemberships(membershipList)
    setMembershipById(byId)
  }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [communityList, membershipList] = await Promise.all([
        communityService.getCommunities(),
        membershipService.getMyMemberships(),
      ])
      applyData(communityList, membershipList)
    } catch (err) {
      console.error('Failed to load communities:', err)
    } finally {
      setLoading(false)
      setLoaded(true)
    }
  }, [])

  useEffect(() => {
    if (!user) {
      setCommunities([])
      setMemberships([])
      setMembershipById({})
      setLoading(false)
      setLoaded(false)
      return undefined
    }
    setLoading(true)
    setLoaded(false)
    load()
  }, [user, load])

  /** Records the community the user last entered/visited (next-login default only). */
  const rememberCommunity = useCallback((communityId) => {
    localStorage.setItem('lastCommunityId', String(communityId))
  }, [])

  /**
   * Default community used only when no explicit :communityId is present in
   * the URL (post-login landing, /dashboard shims, Community Space entry).
   * localStorage `lastCommunityId` is a fallback, never an override.
   */
  const defaultCommunityId = useMemo(() => {
    if (communities.length === 0) return null
    const saved = localStorage.getItem('lastCommunityId')
    if (saved && communities.some((c) => String(c.id) === String(saved))) {
      return String(saved)
    }
    return String(communities[0].id)
  }, [communities])

  const isActiveMember = useCallback((communityId) => {
    const membership = membershipById[String(communityId)]
    return !!membership && membership.status === 'ACTIVE'
  }, [membershipById])

  const isManager = useCallback((communityId) => {
    const membership = membershipById[String(communityId)]
    return !!membership && membership.role === 'MANAGER' && membership.status === 'ACTIVE'
  }, [membershipById])

  return (
    <CommunityContext.Provider value={{
      communities,
      memberships,
      membershipById,
      loading,
      loaded,
      defaultCommunityId,
      rememberCommunity,
      isActiveMember,
      isManager,
      reload: load
    }}>
      {children}
    </CommunityContext.Provider>
  )
}

export function useCommunity() {
  const context = React.useContext(CommunityContext)
  if (!context) {
    throw new Error('useCommunity must be used within CommunityProvider')
  }
  return context
}