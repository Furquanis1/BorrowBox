import React from 'react'
import { useApp } from '../contexts/AppContext'

export default function Toast() {
  const { toast, hideToast } = useApp()

  if (!toast) return null

  const isSuccess = toast.type === 'success'

  return (
    <div
      style={{
        position: 'fixed',
        bottom: '24px',
        right: '24px',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        padding: '12px 20px',
        borderRadius: '10px',
        backgroundColor: isSuccess ? '#10B981' : '#EF4444',
        color: '#FFFFFF',
        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.3)',
        fontWeight: 500,
        fontSize: '14px',
        animation: 'slideIn 0.25s ease-out'
      }}
    >
      <span>{isSuccess ? '✓' : '⚠️'}</span>
      <span>{toast.message}</span>
      <button
        onClick={hideToast}
        style={{
          background: 'none',
          border: 'none',
          color: '#FFFFFF',
          cursor: 'pointer',
          fontSize: '16px',
          padding: '0 4px',
          opacity: 0.8
        }}
      >
        ✕
      </button>
    </div>
  )
}
