import React, { createContext, useCallback, useEffect, useState } from 'react'
import { useAuth } from './AuthContext'
import { communityService, membershipService } from '../services'

export const CommunityContext = createContext()

/**
 * Owns everything about the signed-in user's communities:
 *  - the list of communities they belong to (any membership status),
 *  - their membership record for each community (role + status),
 *  - the currently active community, persisted in localStorage.
 *
 * It loads automatically whenever the signed-in user changes (and clears
 * itself when the user signs out or the session is lost).
 */
export function CommunityProvider({ children }) {
  const { user } = useAuth()
  const [communities, setCommunities] = useState([])
  const [memberships, setMemberships] = useState([])
  const [membershipById, setMembershipById] = useState({})
  const [activeCommunity, setActiveCommunity] = useState(null)

  const applyData = (communityList, membershipList) => {
    const byId = {}
    for (const m of membershipList) {
      byId[String(m.communityId)] = m
    }
    setCommunities(communityList)
    setMemberships(membershipList)
    setMembershipById(byId)
    setActiveCommunity((current) => {
      if (communityList.length === 0) return null
      if (current && communityList.some((c) => String(c.id) === String(current))) return current
      const saved = localStorage.getItem('activeCommunityId')
      if (saved && communityList.some((c) => String(c.id) === String(saved))) return saved
      const firstId = communityList[0].id
      localStorage.setItem('activeCommunityId', String(firstId))
      return firstId
    })
  }

  const load = useCallback(async () => {
    try {
      const [communityList, membershipList] = await Promise.all([
        communityService.getCommunities(),
        membershipService.getMyMemberships(),
      ])
      applyData(communityList, membershipList)
    } catch (err) {
      console.error('Failed to load communities:', err)
    }
  }, [])

  useEffect(() => {
    if (!user) {
      setCommunities([])
      setMemberships([])
      setMembershipById({})
      setActiveCommunity(null)
      return undefined
    }
    load()
  }, [user, load])

  const switchCommunity = useCallback((communityId) => {
    setActiveCommunity(communityId)
    localStorage.setItem('activeCommunityId', String(communityId))
  }, [])

  const isActiveMember = useCallback((communityId) => {
    const membership = membershipById[String(communityId)]
    return !!membership && membership.status === 'ACTIVE'
  }, [membershipById])

  const isManager = useCallback((communityId) => {
    const membership = membershipById[String(communityId)]
    return !!membership && membership.role === 'MANAGER' && membership.status === 'ACTIVE'
  }, [membershipById])

  const activeMembership = membershipById[String(activeCommunity)] || null

  return (
    <CommunityContext.Provider value={{
      communities,
      memberships,
      membershipById,
      activeCommunity,
      activeMembership,
      switchCommunity,
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