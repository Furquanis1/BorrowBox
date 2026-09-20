import React, { useCallback, useMemo, useState } from 'react'
import { useAuth } from '../../contexts/AuthContext'
import { useCommunity } from '../../contexts/CommunityContext'
import { useAsync } from '../../hooks/useAsync'
import { profileService, reputationService, requestService } from '../../services'
import TrustSummary from '../../components/dashboard/TrustSummary'
import ReputationLedgerList from '../../components/dashboard/ReputationLedgerList'
import HistoryList from '../../components/dashboard/HistoryList'
import Spinner from '../../components/ui/Spinner'
import EmptyState from '../../components/ui/EmptyState'

export default function ProfilePage() {
  const { user } = useAuth()
  const { communities, isActiveMember } = useCommunity()
  const [scope, setScope] = useState('')

  const activeCommunities = useMemo(
    () => communities.filter((community) => isActiveMember(community.id)),
    [communities, isActiveMember]
  )

  const fetchProfile = useCallback(
    () => profileService.getTrustProfile(scope || null),
    [scope]
  )
  const profileState = useAsync(fetchProfile, [scope])

  const fetchReputation = useCallback(
    () => reputationService.listForUser(scope || null),
    [scope]
  )
  const reputationState = useAsync(fetchReputation, [scope])

  const fetchMine = useCallback(() => requestService.getMine(), [])
  const fetchLended = useCallback(() => requestService.getLendRequests(), [])
  const mineState = useAsync(fetchMine, [])
  const lendedState = useAsync(fetchLended, [])

  const scopeId = scope ? Number(scope) : null
  const transactions = useMemo(
    () => [...(mineState.data || []), ...(lendedState.data || [])],
    [mineState.data, lendedState.data]
  )
  const scopedTransactions = useMemo(
    () => (scopeId ? transactions.filter((txn) => txn.communityId === scopeId) : transactions),
    [transactions, scopeId]
  )

  const loading = profileState.loading || mineState.loading || lendedState.loading
  const error = profileState.error || mineState.error || lendedState.error

  return (
    <div className="profile-page">
      <header className="requests-header">
        <h2>Profile</h2>
        <p>Your derived trust profile and transaction history.</p>
      </header>

      {activeCommunities.length > 0 && (
        <label className="profile-scope">
          <span>Scope</span>
          <select value={scope} onChange={(event) => setScope(event.target.value)}>
            <option value="">All communities</option>
            {activeCommunities.map((community) => (
              <option key={community.id} value={community.id}>
                {community.name}
              </option>
            ))}
          </select>
        </label>
      )}

      {loading && !profileState.data ? (
        <div className="profile-loading">
          <Spinner />
        </div>
      ) : error && !profileState.data ? (
        <EmptyState
          icon="bi-exclamation-triangle"
          title="Could not load your profile"
          description={error.message}
        />
      ) : (
        <>
          <TrustSummary profile={profileState.data} />

          <ReputationLedgerList events={reputationState.data} />

          <section className="profile-history" aria-label="Transaction history">
            <div className="requests-header">
              <h2>History</h2>
              <p>Loans and returns in this scope.</p>
            </div>
            <HistoryList transactions={scopedTransactions} currentUserId={user?.id} />
          </section>
        </>
      )}
    </div>
  )
}
