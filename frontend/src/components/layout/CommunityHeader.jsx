import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../../contexts/AppContext'
import { useCommunity } from '../../contexts/CommunityContext'
import { useCommunityRoute } from '../../hooks/useCommunityRoute'
import { communityService } from '../../services'

const TYPE_LABELS = {
  HOSTEL: 'Hostel',
  OFFICE: 'Office',
  COLLEGE: 'College',
  HOUSING: 'Housing',
  CLUB: 'Club',
  FRIENDS: 'Friends',
  OTHER: 'Other',
}

const STATUS_LABELS = {
  PENDING: 'Pending approval',
  SUSPENDED: 'Suspended',
  LEFT: 'Left',
  REJECTED: 'Not a member',
}

async function copyText(value) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!copied) throw new Error('Clipboard unavailable')
}

export default function CommunityHeader() {
  const navigate = useNavigate()
  const { showToast } = useApp()
  const { community } = useCommunityRoute()
  const { communities, membershipById, isActiveMember, reload } = useCommunity()
  const [activeManagers, setActiveManagers] = useState(null)
  const [confirmingLeave, setConfirmingLeave] = useState(false)
  const [leaving, setLeaving] = useState(false)

  const membership = community ? membershipById[String(community.id)] : null
  const activeMember = !!membership && membership.status === 'ACTIVE'
  const roleLabel = membership?.role === 'MANAGER' ? 'Manager' : 'Member'
  const membershipLabel = membership
    ? activeMember
      ? roleLabel
      : STATUS_LABELS[membership.status] || membership.status
    : 'Not a member'

  useEffect(() => {
    if (!community || !activeMember) {
      setActiveManagers(null)
      return undefined
    }

    let cancelled = false
    communityService
      .getMembers(community.id, { status: 'ACTIVE', role: 'MANAGER' })
      .then((members) => {
        if (!cancelled) setActiveManagers(Array.isArray(members) ? members : [])
      })
      .catch(() => {
        if (!cancelled) setActiveManagers(null)
      })

    return () => {
      cancelled = true
    }
  }, [community?.id, activeMember])

  const canLeave = activeMember
    && activeManagers !== null
    && !(membership?.role === 'MANAGER' && activeManagers.length <= 1)

  const nextActiveCommunity = communities.find(
    (item) => String(item.id) !== String(community?.id) && isActiveMember(item.id),
  )

  const handleCopyId = async () => {
    if (!community) return
    try {
      await copyText(String(community.id))
      showToast('Copied ID', 'success')
    } catch (err) {
      showToast('Could not copy the community ID', 'danger')
    }
  }

  const handleLeave = async () => {
    if (!community) return
    setLeaving(true)
    try {
      await communityService.leaveCommunity(community.id)
      await reload()
      showToast(`You left ${community.name}`, 'success')
      setConfirmingLeave(false)
      navigate(nextActiveCommunity ? `/communities/${nextActiveCommunity.id}` : '/me/profile')
    } catch (err) {
      showToast(err.message || 'Could not leave this community', 'danger')
    } finally {
      setLeaving(false)
    }
  }

  if (!community) return null

  const memberCount = community.membershipCount || 0
  const badgeText = [
    TYPE_LABELS[community.type] || 'Other',
    `${memberCount} member${memberCount === 1 ? '' : 's'}`,
    membershipLabel,
  ].join(' • ')

  return (
    <header className="community-header">
      <div className="community-header-top">
        <div className="community-header-heading">
          <h1 className="community-header-title">{community.name}</h1>
          <p className="community-header-subtitle">
            {community.description || 'A calm place for your community to share and borrow.'}
          </p>
        </div>
        <div className="community-header-side">
          <div className="community-header-meta">
            <span className="community-header-badge">{badgeText}</span>
            <button
              type="button"
              className="community-id-chip"
              onClick={handleCopyId}
              title="Copy community ID"
              aria-label="Copy community ID"
            >
              <i className="bi bi-hash" aria-hidden="true" />
              ID #{community.id}
            </button>
          </div>
          {canLeave && (
            <div className="community-header-actions">
              {!confirmingLeave ? (
                <button
                  type="button"
                  className="community-leave-btn"
                  onClick={() => setConfirmingLeave(true)}
                >
                  <i className="bi bi-box-arrow-right" aria-hidden="true" />
                  Leave community
                </button>
              ) : (
                <div className="community-leave-confirm" role="alertdialog" aria-label="Confirm leaving community">
                  <span>Leave this community?</span>
                  <button
                    type="button"
                    className="community-leave-cancel"
                    onClick={() => setConfirmingLeave(false)}
                    disabled={leaving}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="community-leave-confirm-btn"
                    onClick={handleLeave}
                    disabled={leaving}
                  >
                    {leaving ? 'Leaving…' : 'Confirm leave'}
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
