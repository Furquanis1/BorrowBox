import React, { useState } from 'react'
import Drawer from '../ui/Drawer'
import Button from '../ui/Button'
import Input from '../ui/Input'
import { requestService, waitlistService } from '../../services'

/**
 * Drawer for requesting a listed item from a community. Supports two modes:
 *  - 'request' (default): creates a normal PENDING transaction.
 *  - 'waitlist': joins the per-asset waitlist (availableUnits === 0).
 *
 * In both modes the borrower states a purpose and a requested duration.
 */
export default function RequestDrawer({ open, onClose, listing, onSubmitted, mode = 'request' }) {
  const [purpose, setPurpose] = useState('')
  const [duration, setDuration] = useState(3)
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (!listing) return null

  const isWaitlist = mode === 'waitlist'

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    const trimmedPurpose = purpose.trim()
    if (!trimmedPurpose) {
      setError('Purpose is required.')
      return
    }
    if (!duration || duration < 1 || duration > 30) {
      setError('Duration must be between 1 and 30 days.')
      return
    }
    setBusy(true)
    try {
      if (isWaitlist) {
        const joined = await waitlistService.join(listing.id, trimmedPurpose, Number(duration))
        onSubmitted?.(joined)
      } else {
        const created = await requestService.create(listing.id, trimmedPurpose, Number(duration))
        onSubmitted?.(created)
      }
    } catch (err) {
      setError(err?.message || 'Could not send the request.')
    } finally {
      setBusy(false)
    }
  }

  const drawerTitle = isWaitlist
    ? `Join waitlist for "${listing.title}"`
    : `Request "${listing.title}"`

  return (
    <Drawer open={open} onClose={onClose} title={drawerTitle} className="request-drawer">
      <form className="request-drawer-form" onSubmit={handleSubmit} noValidate>
        <div className="request-drawer-summary">
          {isWaitlist ? (
            <span className="badge badge-info">
              {listing.waitingCount || 0} waiting
            </span>
          ) : (
            <span className="badge badge-teal">{listing.availableUnits} available</span>
          )}
          <span className="badge badge-neutral">{listing.communityName}</span>
        </div>

        {isWaitlist && (
          <p className="request-drawer-hint">
            No units are available right now. You&apos;ll be queued and automatically
            promoted when a unit is released.
          </p>
        )}

        <Input
          id="request-purpose"
          label="Purpose"
          value={purpose}
          onChange={(event) => setPurpose(event.target.value)}
          placeholder="What do you need it for?"
          autoComplete="off"
        />

        <Input
          id="request-duration"
          label="Requested duration (days)"
          type="number"
          min={1}
          max={30}
          step={1}
          value={duration}
          onChange={(event) => setDuration(event.target.value)}
        />

        {!isWaitlist && (
          <Input
            id="request-note"
            label="Note to the owner (optional)"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder="Anything the owner should know"
            autoComplete="off"
          />
        )}

        {error && (
          <p className="field-error" role="alert">
            {error}
          </p>
        )}

        <div className="request-drawer-actions">
          <Button type="submit" loading={busy} block>
            <i className={isWaitlist ? 'bi bi-clock-history' : 'bi bi-send'} aria-hidden="true" />
            {isWaitlist ? 'Join waitlist' : 'Send request'}
          </Button>
        </div>
      </form>
    </Drawer>
  )
}
