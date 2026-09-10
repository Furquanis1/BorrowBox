import React, { useState } from 'react'
import Drawer from '../ui/Drawer'
import Button from '../ui/Button'
import Input from '../ui/Input'
import { requestService } from '../../services'

/**
 * Lender's counter-offer form. The purpose is optional and falls back to the
 * borrower's original purpose; the duration is required (1-30 days).
 */
export default function CounterOfferDrawer({ open, onClose, transaction, onSubmitted }) {
  const [purpose, setPurpose] = useState('')
  const [duration, setDuration] = useState(transaction?.requestedDurationDays || 3)
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (!transaction) return null

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    if (!duration || duration < 1 || duration > 30) {
      setError('Duration must be between 1 and 30 days.')
      return
    }
    setBusy(true)
    try {
      const updated = await requestService.counterOffer(transaction.id, {
        purpose: purpose.trim() || undefined,
        requestedDurationDays: Number(duration),
        note: note.trim() || undefined,
      })
      onSubmitted?.(updated)
    } catch (err) {
      setError(err?.message || 'Could not send the counter-offer.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={`Counter-offer on "${transaction.title}"`}
      className="request-drawer"
    >
      <form className="request-drawer-form" onSubmit={handleSubmit} noValidate>
        <p className="request-drawer-counter-summary">
          {transaction.borrowerName} asked for &ldquo;{transaction.purpose}&rdquo; over{' '}
          {transaction.requestedDurationDays} days.
        </p>

        <Input
          id="counter-purpose"
          label="Purpose (optional)"
          value={purpose}
          onChange={(event) => setPurpose(event.target.value)}
          placeholder="Leave blank to keep the original purpose"
          autoComplete="off"
        />

        <Input
          id="counter-duration"
          label="Counter duration (days)"
          type="number"
          min={1}
          max={30}
          step={1}
          value={duration}
          onChange={(event) => setDuration(event.target.value)}
        />

        <Input
          id="counter-note"
          label="Note (optional)"
          value={note}
          onChange={(event) => setNote(event.target.value)}
          placeholder="Anything to explain the counter-offer"
          autoComplete="off"
        />

        {error && (
          <p className="field-error" role="alert">
            {error}
          </p>
        )}

        <div className="request-drawer-actions">
          <Button type="submit" loading={busy} block>
            <i className="bi bi-arrow-repeat" aria-hidden="true" />
            Send counter-offer
          </Button>
        </div>
      </form>
    </Drawer>
  )
}