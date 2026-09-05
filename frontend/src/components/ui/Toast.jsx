import React from 'react'
import { useApp } from '../../contexts/AppContext'
import IconButton from './IconButton'

export default function Toast() {
  const { toast, hideToast } = useApp()

  if (!toast) return null

  const tone = toast.type === 'success' ? 'success' : 'danger'

  return (
    <div className={`toast toast-${tone}`} role="alert" aria-live="polite">
      <span className="toast-icon" aria-hidden="true">
        <i className={`bi ${tone === 'success' ? 'bi-check-circle' : 'bi-exclamation-triangle'}`} />
      </span>
      <span className="toast-message">{toast.message}</span>
      <IconButton label="Close notification" onClick={hideToast}>
        <i className="bi bi-x-lg" aria-hidden="true" />
      </IconButton>
    </div>
  )
}