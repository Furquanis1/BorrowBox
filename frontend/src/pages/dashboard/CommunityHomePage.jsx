import React, { useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../../contexts/AppContext'
import { useCommunityRoute } from '../../hooks/useCommunityRoute'
import { useAsync } from '../../hooks/useAsync'
import { listingService, ruleService } from '../../services'
import { ruleSummary } from '../../components/dashboard/RuleValueEditor'

const ADMISSION_LABELS = {
  MANAGER_APPROVAL: 'Manager approval',
  LOCATION_VERIFIED: 'Verified location',
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

export default function CommunityHomePage() {
  const navigate = useNavigate()
  const { showToast } = useApp()
  const { community } = useCommunityRoute()
  const currentCommunityId = community?.id

  const fetchListings = useCallback(() => {
    if (!currentCommunityId) return Promise.resolve([])
    return listingService.getCommunityListings(currentCommunityId).catch(() => [])
  }, [currentCommunityId])

  const fetchRules = useCallback(() => {
    if (!community) return Promise.resolve([])
    return ruleService.listActiveRules(community.id).catch(() => [])
  }, [community?.id])

  const { data: listings } = useAsync(fetchListings, [currentCommunityId])
  const { data: rules } = useAsync(fetchRules, [community?.id])

  const activeRules = useMemo(
    () => (Array.isArray(rules) ? rules.filter((rule) => rule.status === 'ACTIVE') : []),
    [rules],
  )

  const availableItemCount = useMemo(
    () => (listings || []).filter((listing) => listing.availableUnits > 0).length,
    [listings],
  )

  const copyCommunityId = async () => {
    if (!community) return
    try {
      await copyText(String(community.id))
      showToast('Copied ID', 'success')
    } catch (err) {
      showToast('Could not copy the community ID', 'danger')
    }
  }

  if (!community) return null

  const pulseItems = [
    {
      label: 'Active Members',
      value: community.membershipCount || 0,
      icon: 'bi-people',
    },
    {
      label: 'Available Items',
      value: availableItemCount,
      icon: 'bi-box-seam',
    },
    {
      label: 'Active Rules',
      value: activeRules.length,
      icon: 'bi-shield-check',
    },
    {
      label: 'Admission Mode',
      value: ADMISSION_LABELS[community.admissionMode] || community.admissionMode || 'Manager approval',
      icon: 'bi-door-open',
    },
  ]

  return (
    <div className="community-home">
      <section className="community-hub" aria-label="Community overview">
        <div className="community-hub-heading">
          <span className="community-hub-eyebrow">Your circle at a glance</span>
          <h2 className="community-home-section-title community-hub-title">Community Pulse</h2>
          <p>
            A quiet snapshot of what is shared and how this circle looks after its things.
          </p>
        </div>

        <div className="community-pulse">
          {pulseItems.map((item) => (
            <div className="community-pulse-item" key={item.label}>
              <span className="community-pulse-icon" aria-hidden="true">
                <i className={`bi ${item.icon}`} />
              </span>
              <span className="community-pulse-copy">
                <strong>{item.value}</strong>
                <small>{item.label}</small>
              </span>
            </div>
          ))}
        </div>

        <div className="community-hub-grid">
          <section className="community-guidelines" aria-labelledby="community-guidelines-title">
            <div className="community-hub-card-heading">
              <h3 id="community-guidelines-title">Community Guidelines</h3>
              <button
                type="button"
                className="community-hub-link"
                onClick={() => navigate(`/communities/${community.id}/rules`)}
              >
                View all rules →
              </button>
            </div>
            <p className="community-hub-card-subtitle">Active trust rules</p>
            {activeRules.length === 0 ? (
              <p className="community-guidelines-empty">
                No active rules yet. Your managers can set them on the Rules tab.
              </p>
            ) : (
              <ul className="community-guidelines-list">
                {activeRules.slice(0, 3).map((rule) => (
                  <li key={rule.id}>
                    <i className="bi bi-shield-check" aria-hidden="true" />
                    <span>
                      <strong>{(rule.ruleType || 'Rule').replaceAll('_', ' ')}</strong>
                      {ruleSummary(rule) && <small>{ruleSummary(rule)}</small>}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="community-quick-actions" aria-labelledby="community-actions-title">
            <h3 id="community-actions-title">Quick Community Actions</h3>
            <p>Keep the circle moving with a couple of familiar next steps.</p>
            <div className="community-action-list">
              <button
                type="button"
                className="community-action-button"
                onClick={() => navigate('/me/inventory')}
              >
                <i className="bi bi-plus-circle" aria-hidden="true" />
                <span>
                  <strong>Share an item from Inventory</strong>
                  <small>Offer something you own to this circle.</small>
                </span>
                <i className="bi bi-arrow-right" aria-hidden="true" />
              </button>
              <button
                type="button"
                className="community-action-button"
                onClick={copyCommunityId}
              >
                <i className="bi bi-person-plus" aria-hidden="true" />
                <span>
                  <strong>Invite / Share Community ID</strong>
                  <small>Copy ID #{community.id} and send it to someone you trust.</small>
                </span>
                <i className="bi bi-copy" aria-hidden="true" />
              </button>
            </div>
          </section>
        </div>
      </section>

      <section className="community-explore-card" aria-labelledby="community-explore-title">
        <span className="community-explore-card-icon" aria-hidden="true">
          <i className="bi bi-boxes" />
        </span>
        <div className="community-explore-card-copy">
          <span className="community-hub-eyebrow">Browse the catalogue</span>
          <h2 id="community-explore-title">Explore Borrowable Items</h2>
          <p>Everything shared in {community.name} lives one click away in Explore.</p>
        </div>
        <button
          type="button"
          className="community-explore-cta"
          onClick={() => navigate(`/communities/${community.id}/explore`)}
        >
          Explore Borrowable Items →
        </button>
      </section>
    </div>
  )
}
