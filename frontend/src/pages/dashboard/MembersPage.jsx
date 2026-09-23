import React, { useCallback, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'
import { useApp } from '../../contexts/AppContext'
import { useAsync } from '../../hooks/useAsync'
import { communityService, membershipService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'

const MEMBERSHIP_STATUSES = ['PENDING', 'ACTIVE', 'SUSPENDED', 'LEFT', 'REJECTED']
const MEMBERSHIP_ROLES = ['MEMBER', 'MANAGER']

function statusTone(status) {
  if (status === 'ACTIVE') return 'badge-success'
  if (status === 'PENDING') return 'badge-warning'
  if (status === 'SUSPENDED') return 'badge-danger'
  return 'badge-neutral'
}

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString()
}

export default function MembersPage() {
  const { communityId } = useParams()
  const { isManager } = useCommunity()
  const { showToast } = useApp()

  const [filters, setFilters] = useState({ status: '', role: '' })
  const [appliedFilters, setAppliedFilters] = useState({})
  const [actingMembershipId, setActingMembershipId] = useState(null)

  const fetchMembers = useCallback(() => {
    if (!communityId) return Promise.resolve([])
    return communityService.getMembers(communityId, {
      status: appliedFilters.status || undefined,
      role: appliedFilters.role || undefined,
    })
  }, [communityId, appliedFilters])

  const fetchPending = useCallback(() => {
    if (!communityId || !isManager(communityId)) return Promise.resolve([])
    return communityService.getPendingMembers(communityId)
  }, [communityId, isManager])

  const { data: members, loading, error, reload } = useAsync(fetchMembers, [communityId, appliedFilters])
  const { data: pending, reload: reloadPending } = useAsync(fetchPending, [communityId, isManager])

  const applyFilters = () => setAppliedFilters({ status: filters.status, role: filters.role })
  const clearFilters = () => {
    setFilters({ status: '', role: '' })
    setAppliedFilters({})
  }

  const act = async (action, membershipId, successMessage) => {
    setActingMembershipId(membershipId)
    try {
      await action(communityId, membershipId)
      showToast(successMessage)
      await Promise.all([reload(), reloadPending()])
    } catch (err) {
      showToast(err?.message || 'Request failed', 'error')
    } finally {
      setActingMembershipId(null)
    }
  }

  const suspend = (membershipId) => act(communityService.suspendMember, membershipId, 'Member suspended.')
  const reinstate = (membershipId) => act(communityService.reinstateMember, membershipId, 'Member reinstated.')
  const remove = (membershipId) => act(communityService.removeMember, membershipId, 'Member removed.')

  const decide = async (membershipId, decision) => {
    setActingMembershipId(membershipId)
    try {
      await membershipService.decideMembership(membershipId, decision)
      showToast(decision === 'APPROVE' ? 'Request approved.' : 'Request rejected.')
      await Promise.all([reload(), reloadPending()])
    } catch (err) {
      showToast(err?.message || 'Request failed', 'error')
    } finally {
      setActingMembershipId(null)
    }
  }

  const manager = isManager(communityId)

  return (
    <div className="members-page">
      {manager && pending && pending.length > 0 && (
        <section aria-labelledby="pending-members-title">
          <h2 id="pending-members-title" className="manager-section-title">
            Pending requests
          </h2>
          <ul className="member-list">
            {pending.map((membership) => (
              <li key={membership.id} className="member-item">
                <div className="member-item-main">
                  <span className="member-item-name">{membership.userFullName}</span>
                  <span className={`badge ${statusTone(membership.status)}`}>{membership.status}</span>
                  {membership.contextMetadata && Object.keys(membership.contextMetadata).length > 0 && (
                    <span className="member-item-meta">
                      {Object.entries(membership.contextMetadata)
                        .map(([key, value]) => `${key}: ${value}`)
                        .join(' · ')}
                    </span>
                  )}
                </div>
                <div className="member-item-actions">
                  <Button
                    variant="primary"
                    size="sm"
                    disabled={actingMembershipId === membership.id}
                    onClick={() => decide(membership.id, 'APPROVE')}
                  >
                    Approve
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={actingMembershipId === membership.id}
                    onClick={() => decide(membership.id, 'REJECT')}
                  >
                    Reject
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section aria-labelledby="members-title">
        <h2 id="members-title" className="manager-section-title">
          Members
        </h2>

        <div className="member-filters">
          <div className="form-group">
            <label htmlFor="filter-member-status">Status</label>
            <select
              id="filter-member-status"
              className="select"
              value={filters.status}
              onChange={(event) => setFilters((prev) => ({ ...prev, status: event.target.value }))}
            >
              <option value="">All statuses</option>
              {MEMBERSHIP_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="filter-member-role">Role</label>
            <select
              id="filter-member-role"
              className="select"
              value={filters.role}
              onChange={(event) => setFilters((prev) => ({ ...prev, role: event.target.value }))}
            >
              <option value="">All roles</option>
              {MEMBERSHIP_ROLES.map((role) => (
                <option key={role} value={role}>
                  {role}
                </option>
              ))}
            </select>
          </div>
          <div className="member-filter-actions">
            <Button variant="primary" size="sm" onClick={applyFilters}>
              Apply
            </Button>
            <Button variant="outline" size="sm" onClick={clearFilters}>
              Clear
            </Button>
          </div>
        </div>

        {loading ? (
          <Spinner />
        ) : error ? (
          <EmptyState
            icon="bi-exclamation-triangle"
            title="Could not load members"
            description={error.message}
            action={
              <Button variant="outline" onClick={reload}>
                <i className="bi bi-arrow-clockwise" aria-hidden="true" />
                Try again
              </Button>
            }
          />
        ) : members.length === 0 ? (
          <EmptyState
            icon="bi-people"
            title="No members here yet"
            description="Members of this community will show up here."
          />
        ) : (
          <ul className="member-list">
            {members.map((membership) => (
              <li key={membership.id} className="member-item">
                <div className="member-item-main">
                  <span className="member-item-name">{membership.userFullName}</span>
                  <span className={`badge ${statusTone(membership.status)}`}>{membership.status}</span>
                  <span className={`badge ${membership.role === 'MANAGER' ? 'badge-teal' : 'badge-neutral'}`}>
                    {membership.role}
                  </span>
                  <span className="member-item-meta">
                    Joined {formatDate(membership.joinedAt)}
                    {membership.contextMetadata && Object.keys(membership.contextMetadata).length > 0 && (
                      <> · {Object.entries(membership.contextMetadata)
                        .map(([key, value]) => `${key}: ${value}`)
                        .join(' · ')}</>
                    )}
                  </span>
                </div>
                {manager && membership.status !== 'LEFT' && (
                  <div className="member-item-actions">
                    {membership.status === 'ACTIVE' && (
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={actingMembershipId === membership.id}
                        onClick={() => suspend(membership.id)}
                      >
                        Suspend
                      </Button>
                    )}
                    {membership.status === 'SUSPENDED' && (
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={actingMembershipId === membership.id}
                        onClick={() => reinstate(membership.id)}
                      >
                        Reinstate
                      </Button>
                    )}
                    {(membership.status === 'ACTIVE' || membership.status === 'SUSPENDED') && (
                      <Button
                        variant="danger"
                        size="sm"
                        disabled={actingMembershipId === membership.id}
                        onClick={() => remove(membership.id)}
                      >
                        Remove
                      </Button>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}