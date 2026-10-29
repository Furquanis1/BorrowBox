import React, { useState, useEffect, useCallback } from 'react'
import { useApp } from '../../contexts/AppContext'
import { notificationService } from '../../services'

const TYPE_LABELS = {
  LOAN_APPROVED: 'Loan approved',
  LOAN_REJECTED: 'Loan rejected',
  LOAN_CANCELLED: 'Loan cancelled',
  HANDOVER_SCHEDULED: 'Handover scheduled',
  LOAN_STARTED: 'Loan started',
  RECEIPT_CONFIRMED: 'Receipt confirmed',
  RETURN_INITIATED: 'Return initiated',
  RETURN_HANDOVER_REPORTED: 'Return handback reported',
  RETURN_DISPUTED: 'Return disputed',
  LOAN_COMPLETED: 'Loan completed',
  EXTENSION_REQUESTED: 'Extension requested',
  EXTENSION_APPROVED: 'Extension approved',
  EXTENSION_REJECTED: 'Extension rejected',
  EXTENSION_COUNTERED: 'Extension countered',
  EXTENSION_COUNTER_ACCEPTED: 'Extension counter accepted',
  EXTENSION_COUNTER_REJECTED: 'Extension counter rejected',
}

export default function NotificationPreferences() {
  const { showToast } = useApp()
  const [preferences, setPreferences] = useState([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(new Set())

  const fetchPreferences = useCallback(async () => {
    setLoading(true)
    try {
      const prefs = await notificationService.listPreferences()
      setPreferences(prefs)
    } catch (err) {
      console.error('Failed to load preferences:', err)
      showToast('Failed to load notification preferences', 'danger')
    } finally {
      setLoading(false)
    }
  }, [showToast])

  const togglePreference = async (type, currentEnabled) => {
    const nextEnabled = !currentEnabled
    setSaving((prev) => new Set(prev).add(type))
    try {
      const updated = await notificationService.setPreference(type, nextEnabled)
      setPreferences((prev) =>
        prev.map((p) => (p.type === updated.type ? updated : p))
      )
      showToast(
        updated.enabled
          ? 'Notifications enabled'
          : 'Notifications disabled',
        'success'
      )
    } catch (err) {
      showToast('Failed to update notification preference', 'danger')
    } finally {
      setSaving((prev) => {
        const next = new Set(prev)
        next.delete(type)
        return next
      })
    }
  }

  useEffect(() => {
    fetchPreferences()
  }, [fetchPreferences])

  if (loading) {
    return (
      <div className="notification-preferences-loading">
        <span className="spinner-sm" />
        Loading preferences
      </div>
    )
  }

  return (
    <section className="notification-preferences">
      <h2>Notification preferences</h2>
      <p className="notification-preferences-desc">
        Choose which notification types you&rsquo;d like to receive.
      </p>

      <div className="notification-preference-list">
        {preferences.map((pref) => (
          <div
            key={pref.type}
            className="notification-preference-item"
          >
            <label className="notification-preference-label">
              <span className="notification-preference-name">{TYPE_LABELS[pref.type] || pref.type}</span>
              <input
                type="checkbox"
                checked={pref.enabled}
                onChange={() => togglePreference(pref.type, pref.enabled)}
                disabled={saving.has(pref.type)}
                aria-label={`${TYPE_LABELS[pref.type] || pref.type}: ${pref.enabled ? 'enabled' : 'disabled'}`}
              />
              <span className="notification-preference-toggle">
                <span className="notification-preference-toggle-thumb" />
              </span>
            </label>
          </div>
        ))}
      </div>

      {preferences.length === 0 && (
        <div className="empty-state">
          <span className="empty-state-icon" aria-hidden="true">
            <i className="bi bi-bell" />
          </span>
          <p className="empty-state-title">No notification preferences</p>
          <p className="empty-state-description">No notification types are available.</p>
        </div>
      )}
    </section>
  )
}
