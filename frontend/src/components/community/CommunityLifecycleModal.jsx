import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../../contexts/AppContext'
import { useCommunity } from '../../contexts/CommunityContext'
import { communityService } from '../../services'

const COMMUNITY_TYPES = [
  { value: 'COLLEGE', label: 'College' },
  { value: 'HOSTEL', label: 'Hostel' },
  { value: 'OFFICE', label: 'Office' },
  { value: 'FRIENDS', label: 'Friends / Apartment' },
  { value: 'HOUSING', label: 'Housing' },
  { value: 'CLUB', label: 'Club' },
  { value: 'OTHER', label: 'Other' },
]

const ADMISSION_MODES = [
  { value: 'MANAGER_APPROVAL', label: 'Manager approval' },
  { value: 'LOCATION_VERIFIED', label: 'Verified location' },
]

const EMPTY_FORM = {
  name: '',
  description: '',
  type: 'COLLEGE',
  admissionMode: 'MANAGER_APPROVAL',
  latitude: '',
  longitude: '',
  radius: '',
}

function getCurrentPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('This browser cannot provide a location.'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
      }),
      () => reject(new Error('Location permission is required to join this community.')),
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 },
    )
  })
}

export default function CommunityLifecycleModal({ open, initialMode = 'create', onClose }) {
  const navigate = useNavigate()
  const { showToast } = useApp()
  const { reload, rememberCommunity } = useCommunity()

  const [mode, setMode] = useState(initialMode)
  const [form, setForm] = useState(EMPTY_FORM)
  const [search, setSearch] = useState('')
  const [searched, setSearched] = useState(null)
  const [room, setRoom] = useState('')
  const [memberRole, setMemberRole] = useState('')
  const [busy, setBusy] = useState(false)
  const [busyCommunityId, setBusyCommunityId] = useState(null)
  const [error, setError] = useState('')
  const [joinResult, setJoinResult] = useState(null)

  useEffect(() => {
    if (!open) return
    setMode(initialMode)
    setError('')
    setJoinResult(null)
    setSearch('')
    setSearched(null)
  }, [open, initialMode])

  if (!open) return null

  const updateForm = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }))
  }

  const switchMode = (nextMode) => {
    setMode(nextMode)
    setError('')
    setJoinResult(null)
  }

  const handleCreate = async (event) => {
    event.preventDefault()
    setError('')

    if (!form.name.trim()) {
      setError('Give your community a name to continue.')
      return
    }

    if (
      form.admissionMode === 'LOCATION_VERIFIED'
      && (form.latitude === '' || form.longitude === '' || form.radius === '')
    ) {
      setError('Verified-location communities need a latitude, longitude, and radius.')
      return
    }

    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      type: form.type,
      admissionMode: form.admissionMode,
    }

    if (form.admissionMode === 'LOCATION_VERIFIED') {
      payload.locationLatitude = Number(form.latitude)
      payload.locationLongitude = Number(form.longitude)
      payload.locationRadiusM = Number(form.radius)
    }

    setBusy(true)
    try {
      const created = await communityService.createCommunity(payload)
      await reload()
      rememberCommunity(created.id)
      showToast(`Community created — you are the manager of ${created.name}`, 'success')
      setForm(EMPTY_FORM)
      onClose()
      navigate(`/communities/${created.id}`)
    } catch (err) {
      setError(err.message || 'Could not create this community. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  const handleSearch = async (event) => {
    event.preventDefault()
    setError('')
    setSearched(null)

    const value = search.trim()
    if (!/^\d+$/.test(value)) {
      setError('Enter the numeric community ID shared by a manager.')
      return
    }

    setBusy(true)
    try {
      const community = await communityService.getCommunity(value)
      setSearched(community)
    } catch (err) {
      setError(err.message || 'No community was found for that ID.')
    } finally {
      setBusy(false)
    }
  }

  const handleJoin = async (community) => {
    setError('')
    setJoinResult(null)
    setBusyCommunityId(String(community.id))

    try {
      const contextMetadata = {}
      if (room.trim()) contextMetadata.room = room.trim()
      if (memberRole.trim()) contextMetadata.role = memberRole.trim()

      const payload = { contextMetadata }
      if (community.admissionMode === 'LOCATION_VERIFIED') {
        const position = await getCurrentPosition()
        payload.latitude = position.latitude
        payload.longitude = position.longitude
      }

      const response = await communityService.joinCommunity(community.id, payload)
      const status = response?.status
      const active = status === 'ACTIVE'
      const message = active
        ? 'Joined!'
        : 'Request sent — pending manager approval'

      setJoinResult({ community, status, message })
      showToast(message, 'success')
      await reload()
    } catch (err) {
      setError(err.message || 'Could not send your join request. Please try again.')
    } finally {
      setBusyCommunityId(null)
    }
  }

  return (
    <div
      className="lifecycle-backdrop modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        className="lifecycle-modal modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="lifecycle-modal-title"
      >
        <div className="lifecycle-dialog-header">
          <div>
            <p className="lifecycle-eyebrow">Community onboarding</p>
            <h2 id="lifecycle-modal-title" className="lifecycle-title">
              {mode === 'create' ? 'Create a community' : 'Join a community'}
            </h2>
          </div>
          <button
            type="button"
            className="lifecycle-close"
            onClick={onClose}
            aria-label="Close community onboarding"
          >
            <i className="bi bi-x-lg" aria-hidden="true" />
          </button>
        </div>

        <div className="lifecycle-tabs" role="tablist" aria-label="Community actions">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'create'}
            className={`lifecycle-tab${mode === 'create' ? ' active' : ''}`}
            onClick={() => switchMode('create')}
          >
            Create Community
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'join'}
            className={`lifecycle-tab${mode === 'join' ? ' active' : ''}`}
            onClick={() => switchMode('join')}
          >
            Join Community
          </button>
        </div>

        {error && (
          <p className="lifecycle-error" role="alert">
            {error}
          </p>
        )}

        {mode === 'create' ? (
          <form className="lifecycle-form" onSubmit={handleCreate}>
            <label className="lifecycle-field">
              <span>Community name</span>
              <input
                className="lifecycle-input"
                value={form.name}
                onChange={updateForm('name')}
                placeholder="e.g. CSE Department"
                maxLength={120}
                required
              />
            </label>

            <label className="lifecycle-field">
              <span>Description</span>
              <textarea
                className="lifecycle-textarea"
                value={form.description}
                onChange={updateForm('description')}
                placeholder="What is this community for?"
                rows={3}
              />
            </label>

            <div className="lifecycle-field-row">
              <label className="lifecycle-field">
                <span>Type</span>
                <select
                  className="lifecycle-select"
                  value={form.type}
                  onChange={updateForm('type')}
                >
                  {COMMUNITY_TYPES.map((type) => (
                    <option key={type.value} value={type.value}>{type.label}</option>
                  ))}
                </select>
              </label>

              <label className="lifecycle-field">
                <span>Admission</span>
                <select
                  className="lifecycle-select"
                  value={form.admissionMode}
                  onChange={updateForm('admissionMode')}
                >
                  {ADMISSION_MODES.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
            </div>

            {form.admissionMode === 'LOCATION_VERIFIED' && (
              <div className="lifecycle-location-grid">
                <label className="lifecycle-field">
                  <span>Latitude</span>
                  <input
                    className="lifecycle-input"
                    type="number"
                    step="any"
                    value={form.latitude}
                    onChange={updateForm('latitude')}
                    placeholder="28.6139"
                    required
                  />
                </label>
                <label className="lifecycle-field">
                  <span>Longitude</span>
                  <input
                    className="lifecycle-input"
                    type="number"
                    step="any"
                    value={form.longitude}
                    onChange={updateForm('longitude')}
                    placeholder="77.2090"
                    required
                  />
                </label>
                <label className="lifecycle-field">
                  <span>Radius (m)</span>
                  <input
                    className="lifecycle-input"
                    type="number"
                    min="1"
                    value={form.radius}
                    onChange={updateForm('radius')}
                    placeholder="500"
                    required
                  />
                </label>
              </div>
            )}

            <div className="lifecycle-footer">
              <button
                type="button"
                className="lifecycle-secondary"
                onClick={onClose}
                disabled={busy}
              >
                Cancel
              </button>
              <button type="submit" className="lifecycle-primary" disabled={busy}>
                {busy ? 'Creating…' : 'Create community'}
              </button>
            </div>
          </form>
        ) : (
          <div className="lifecycle-join">
            <form className="lifecycle-search" onSubmit={handleSearch}>
              <input
                className="lifecycle-input lifecycle-search-input"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Community ID shared by a manager"
                aria-label="Search communities by ID"
              />
              <button type="submit" className="lifecycle-secondary" disabled={busy}>
                Find community
              </button>
            </form>

            <div className="lifecycle-context-grid">
              <input
                className="lifecycle-input"
                value={room}
                onChange={(event) => setRoom(event.target.value)}
                placeholder="Room / floor (optional)"
                aria-label="Room or floor"
              />
              <input
                className="lifecycle-input"
                value={memberRole}
                onChange={(event) => setMemberRole(event.target.value)}
                placeholder="Your role (optional)"
                aria-label="Your role in the community"
              />
            </div>

            <p className="lifecycle-hint">
              Enter the exact numeric Community ID shared by a manager. Groups are private and are
              never suggested or browsable.
            </p>

            <div className="lifecycle-discovery-heading">
              <span>Lookup result</span>
            </div>

            <div className="lifecycle-candidate-list">
              {!searched ? (
                <div className="lifecycle-empty">
                  <i className="bi bi-people" aria-hidden="true" />
                  <span>No community loaded yet. Search by exact ID to find one.</span>
                </div>
              ) : (
                (() => {
                  const isBusy = busyCommunityId === String(searched.id)
                  const locationVerified = searched.admissionMode === 'LOCATION_VERIFIED'

                  return (
                    <div className="lifecycle-candidate" key={searched.id}>
                      <div className="lifecycle-candidate-info">
                        <span className="lifecycle-candidate-name">{searched.name}</span>
                        <span className="lifecycle-candidate-meta">
                          {searched.description || 'A BorrowBox community'}
                        </span>
                        <span className="lifecycle-candidate-badges">
                          <span className="lifecycle-id-badge">ID #{searched.id}</span>
                          <span className="lifecycle-mini-pill">
                            {searched.membershipCount || 0} members
                          </span>
                          {locationVerified && (
                            <span className="lifecycle-mini-pill">Verified location</span>
                          )}
                        </span>
                      </div>
                      <button
                        type="button"
                        className="lifecycle-primary lifecycle-join-btn"
                        onClick={() => handleJoin(searched)}
                        disabled={isBusy || busy}
                      >
                        {isBusy
                          ? 'Joining…'
                          : locationVerified
                            ? 'Join with location'
                            : 'Request to join'}
                      </button>
                    </div>
                  )
                })()
              )}
            </div>

            {joinResult && (
              <div className="lifecycle-result" role="status">
                <i className="bi bi-check-circle-fill" aria-hidden="true" />
                <div>
                  <strong>{joinResult.message}</strong>
                  <span>{joinResult.community.name}</span>
                </div>
              </div>
            )}

            <div className="lifecycle-footer">
              <button type="button" className="lifecycle-secondary" onClick={onClose}>
                Done
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
