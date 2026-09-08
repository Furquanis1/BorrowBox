import React from 'react'
import { useCommunity } from '../../contexts/CommunityContext'
import { useCommunityRoute } from '../../hooks/useCommunityRoute'

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

export default function CommunityHeader() {
  const { community } = useCommunityRoute()
  const { membershipById } = useCommunity()

  if (!community) return null

  const membership = membershipById[String(community.id)]
  const activeMember = membership && membership.status === 'ACTIVE'
  const roleLabel = membership?.role === 'MANAGER' ? 'Manager' : 'Member'
  const membershipLabel = membership
    ? activeMember
      ? roleLabel
      : STATUS_LABELS[membership.status] || membership.status
    : 'Not a member'

  const memberCount = community.membershipCount || 0
  const meta = [
    TYPE_LABELS[community.type] || 'Other',
    `${memberCount} member${memberCount === 1 ? '' : 's'}`,
    membershipLabel,
  ].join(' · ')

  return (
    <div className="community-header">
      <h1 className="community-header-title">{community.name}</h1>
      <p className="community-header-meta">{meta}</p>
      {community.description && (
        <p className="community-header-desc">{community.description}</p>
      )}
    </div>
  )
}