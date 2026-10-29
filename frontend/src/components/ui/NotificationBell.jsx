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

export default function NotificationBell() {
  const { showToast } = useApp()
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [isOpen, setIsOpen] = useState(false)
  const [loading, setLoading] = useState(true)

  const fetchNotifications = useCallback(async () => {
    setLoading(true)
    try {
      const [notifs, countRes] = await Promise.all([
        notificationService.listNotifications(),
        notificationService.getUnreadCount(),
      ])
      setNotifications(notifs)
      setUnreadCount(countRes)
    } catch (err) {
      console.error('Failed to fetch notifications:', err)
    } finally {
      setLoading(false)
    }
  }, [])

  const handleMarkAsRead = async (id) => {
    try {
      await notificationService.markAsRead(id)
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, status: 'READ', readAt: new Date().toISOString() } : n))
      )
      setUnreadCount((prev) => Math.max(0, prev - 1))
      showToast('Notification marked as read', 'success')
    } catch (err) {
      showToast('Failed to mark notification as read', 'danger')
    }
  }

  useEffect(() => {
    fetchNotifications()
  }, [fetchNotifications])

  return (
    <div className="notification-bell" ref={null}>
      <button
        type="button"
        className={`notification-bell-btn ${unreadCount > 0 ? 'has-unread' : ''}`}
        aria-label={`Notifications${unreadCount > 0 ? ', ' + unreadCount + ' unread' : ''}`}
        onClick={() => setIsOpen((prev) => !prev)}
      >
        <i className={`bi bi-${unreadCount > 0 ? 'bell-fill' : 'bell'}`} aria-hidden="true" />
        {unreadCount > 0 && (
          <span className="notification-badge" aria-label={unreadCount + ' unread notifications'}>
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="notification-dropdown" onClick={(e) => e.stopPropagation()}>
          <div className="notification-dropdown-header">
            <h3>Notifications</h3>
            <span className="notification-badge">{unreadCount} unread</span>
          </div>

          {loading ? (
            <div className="notification-loading">
              <span className="spinner-sm" />
              Loading notifications…
            </div>
          ) : notifications.length === 0 ? (
            <div className="notification-empty">
              <i className="bi bi-bell" aria-hidden="true" />
              <p>No notifications yet.</p>
            </div>
          ) : (
            <ul className="notification-list">
              {notifications.map((n) => (
                <li
                  key={n.id}
                  className={`notification-item ${n.status === 'UNREAD' ? 'unread' : 'read'}`}
                  onClick={() => {
                    if (n.status === 'UNREAD') handleMarkAsRead(n.id)
                  }}
                >
                  <div className="notification-item-main">
                    <div className="notification-item-title">{n.title}</div>
                    <div className="notification-item-message">{n.message}</div>
                    <time className="notification-item-time">{new Date(n.createdAt).toLocaleString()}</time>
                  </div>
                  <div className="notification-item-actions">
                    {n.status === 'UNREAD' && (
                      <button
                        type="button"
                        className="btn btn-sm btn-outline"
                        onClick={(e) => {
                          e.stopPropagation()
                          handleMarkAsRead(n.id)
                        }}
                      >
                        Mark as read
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
