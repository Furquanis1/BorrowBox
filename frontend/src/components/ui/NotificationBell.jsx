import React, { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../../contexts/AppContext'
import { notificationService } from '../../services'

const REQUEST_STAGE_TYPES = new Set([
  'REQUEST_CREATED',
  'REQUEST_COUNTERED',
  'LOAN_APPROVED',
  'LOAN_REJECTED',
  'LOAN_CANCELLED',
  'HANDOVER_SCHEDULED',
])

const LOAN_STAGE_TYPES = new Set([
  'LOAN_STARTED',
  'RECEIPT_CONFIRMED',
  'RETURN_INITIATED',
  'RETURN_HANDOVER_REPORTED',
  'RETURN_DISPUTED',
  'LOAN_COMPLETED',
  'LOAN_DUE_SOON',
  'LOAN_OVERDUE',
])

const MEMBERSHIP_TYPES = new Set([
  'MEMBERSHIP_REQUEST_PENDING',
  'MEMBERSHIP_APPROVED',
  'MEMBERSHIP_REJECTED',
])

function notificationIcon(type) {
  if (MEMBERSHIP_TYPES.has(type)) return 'bi-people'
  if (type?.startsWith('EXTENSION_')) return 'bi-clock-history'
  if (LOAN_STAGE_TYPES.has(type)) return 'bi-arrow-left-right'
  if (REQUEST_STAGE_TYPES.has(type)) return 'bi-inbox'
  return 'bi-bell'
}

function notificationTarget(notification) {
  const type = notification?.type
  if (!type) return null
  const isExtension = type.startsWith('EXTENSION_')
  const base = REQUEST_STAGE_TYPES.has(type)
    ? '/me/requests'
    : LOAN_STAGE_TYPES.has(type) || isExtension
      ? '/me/loans'
      : null
  if (!base) return null
  return `${base}${notification.transactionId != null ? `?tx=${notification.transactionId}` : ''}`
}

export default function NotificationBell() {
  const { showToast } = useApp()
  const navigate = useNavigate()
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [isOpen, setIsOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [markingIds, setMarkingIds] = useState(() => new Set())

  const containerRef = useRef(null)
  const notificationsRef = useRef([])
  const fetchInFlightRef = useRef(false)
  const hasLoadedRef = useRef(false)
  const dataVersionRef = useRef(0)
  const markingIdsRef = useRef(new Set())

  const fetchNotifications = useCallback(async () => {
    if (fetchInFlightRef.current) return
    fetchInFlightRef.current = true
    const versionAtStart = dataVersionRef.current
    if (!hasLoadedRef.current) setLoading(true)
    try {
      const [notifs, countRes] = await Promise.all([
        notificationService.listNotifications(),
        notificationService.getUnreadCount(),
      ])
      if (versionAtStart !== dataVersionRef.current) return
      notificationsRef.current = notifs
      setNotifications(notifs)
      setUnreadCount(countRes)
    } catch (err) {
      console.error('Failed to fetch notifications:', err)
    } finally {
      fetchInFlightRef.current = false
      hasLoadedRef.current = true
      setLoading(false)
    }
  }, [])

  const handleMarkAsRead = useCallback(async (id) => {
    if (markingIdsRef.current.has(id)) return

    markingIdsRef.current.add(id)
    setMarkingIds(new Set(markingIdsRef.current))

    try {
      const updated = await notificationService.markAsRead(id)
      const next = notificationsRef.current.map((notification) =>
        notification.id === id
          ? { ...notification, ...(updated || {}) }
          : notification
      )
      notificationsRef.current = next
      dataVersionRef.current += 1
      setNotifications(next)
      setUnreadCount(next.filter((notification) => notification.status === 'UNREAD').length)
      showToast('Notification marked as read', 'success')
    } catch (err) {
      showToast('Failed to mark notification as read', 'danger')
    } finally {
      markingIdsRef.current.delete(id)
      setMarkingIds(new Set(markingIdsRef.current))
    }
  }, [showToast])

  useEffect(() => {
    fetchNotifications()
  }, [fetchNotifications])

  useEffect(() => {
    if (isOpen) fetchNotifications()
  }, [isOpen, fetchNotifications])

  useEffect(() => {
    const handleWindowFocus = () => fetchNotifications()
    window.addEventListener('focus', handleWindowFocus)
    return () => window.removeEventListener('focus', handleWindowFocus)
  }, [fetchNotifications])

  useEffect(() => {
    if (!isOpen) return

    const handleMouseDown = (event) => {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setIsOpen(false)
      }
    }
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') setIsOpen(false)
    }

    document.addEventListener('mousedown', handleMouseDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleMouseDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  return (
    <div className="notification-bell" ref={containerRef}>
      <button
        type="button"
        className={`notification-bell-btn ${unreadCount > 0 ? 'has-unread' : ''}`}
        aria-label={`Notifications${unreadCount > 0 ? ', ' + unreadCount + ' unread' : ''}`}
        aria-expanded={isOpen}
        aria-haspopup="true"
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
              <div className="notification-empty-card">
                <span className="notification-empty-icon" aria-hidden="true">
                  <i className="bi bi-bell-check" />
                </span>
                <strong>All caught up</strong>
                <p>
                  Borrow decisions, handovers, returns, dispute outcomes, and community membership
                  updates will show up here when there is something new to tell you.
                </p>
              </div>
            </div>
          ) : (
            <ul className="notification-list">
              {notifications.map((n) => (
                <li
                  key={n.id}
                  className={`notification-item ${n.status === 'UNREAD' ? 'unread' : 'read'}${MEMBERSHIP_TYPES.has(n.type) ? ' membership-notification' : ''}`}
                  onClick={() => {
                    if (n.status === 'UNREAD') handleMarkAsRead(n.id)
                    setIsOpen(false)
                    const target = notificationTarget(n)
                    if (target) navigate(target)
                  }}
                >
                  <span className="notification-item-icon" aria-hidden="true">
                    <i className={`bi ${notificationIcon(n.type)}`} />
                  </span>
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
                        disabled={markingIds.has(n.id)}
                        aria-busy={markingIds.has(n.id)}
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
