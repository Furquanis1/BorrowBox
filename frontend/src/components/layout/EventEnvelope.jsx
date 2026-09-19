import React, { useEffect, useCallback, useRef, useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useEvents } from '../../contexts/EventContext'
import { useAuth } from '../../contexts/AuthContext'
import EventToast from '../ui/EventToast'

const navigationMap = {
  REQUEST_APPROVED: '/me/requests',
  REQUEST_REJECTED: '/me/requests',
  REQUEST_CANCELLED: '/me/requests',
  HANDOVER_SCHEDULED: '/me/loans',
  LOAN_STARTED: '/me/loans',
  HANDOVER_CONFIRMED: '/me/loans',
  HANDOVER_DISPUTED: '/me/loans',
  EXTENSION_REQUESTED: '/me/loans',
  EXTENSION_APPROVED: '/me/loans',
  EXTENSION_REJECTED: '/me/loans',
  EXTENSION_COUNTERED: '/me/loans',
  EXTENSION_COUNTER_ACCEPTED: '/me/loans',
  EXTENSION_COUNTER_REJECTED: '/me/loans',
  RETURN_INITIATED: '/me/loans',
  RETURN_REPORTED: '/me/loans',
  LOAN_COMPLETED: '/me/loans',
  RETURN_DISPUTED: '/me/loans',
  WAITLIST_PROMOTED: '/me/requests'
}

export default function EventEnvelopeController() {
  const { deliveries, markRead, dismiss } = useEvents()
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  // Single source of truth for envelope visibility:
  //   null                             -> idle, an unread event may be presented
  //   { delivery, resolved: false }    -> envelope presenting this delivery
  //   { delivery, resolved: true }     -> user acted on this delivery, envelope closed;
  //                                        no auto-reopen until a later focus/route refresh
  const [session, setSession] = useState(null)

  const envelopeRef = useRef(null)
  const previousActiveElement = useRef(null)

  const latestUnread = deliveries.find(d => d.status === 'UNREAD')

  // Present the newest unread envelope only while idle.
  useEffect(() => {
    if (user && latestUnread && !session) {
      setSession({ delivery: latestUnread, resolved: false })
    }
  }, [latestUnread, user, session])

  // A resolved session may begin a new cycle only on window focus,
  // so the next unread appears on a later refresh instead of stacking.
  useEffect(() => {
    const clearResolved = () => setSession(current => (current && current.resolved ? null : current))
    window.addEventListener('focus', clearResolved)
    return () => window.removeEventListener('focus', clearResolved)
  }, [])

  // A route change also ends a resolved session.
  useEffect(() => {
    setSession(current => (current && current.resolved ? null : current))
  }, [location.pathname])

  const handleOpen = useCallback(async () => {
    if (!session) return
    previousActiveElement.current = document.activeElement
    await markRead(session.delivery.deliveryId)
    const targetPath = navigationMap[session.delivery.eventType] || '/me/requests'
    setSession({ delivery: session.delivery, resolved: true })
    navigate(targetPath)
  }, [session, markRead, navigate])

  const handleDismiss = useCallback(async () => {
    if (!session) return
    await dismiss(session.delivery.deliveryId)
    setSession({ delivery: session.delivery, resolved: true })
  }, [session, dismiss])

  // Trap focus within envelope when open
  useEffect(() => {
    if (session && !session.resolved && envelopeRef.current) {
      const focusable = envelopeRef.current.querySelectorAll(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
      )
      const first = focusable[0]
      const last = focusable[focusable.length - 1]

      const handleTab = (e) => {
        if (e.key !== 'Tab') return
        if (e.shiftKey) {
          if (document.activeElement === first) {
            e.preventDefault()
            last.focus()
          }
        } else {
          if (document.activeElement === last) {
            e.preventDefault()
            first.focus()
          }
        }
      }

      envelopeRef.current.addEventListener('keydown', handleTab)
      first?.focus()

      return () => envelopeRef.current.removeEventListener('keydown', handleTab)
    }
  }, [session])

  // Return focus when envelope closes
  useEffect(() => {
    return () => {
      if (previousActiveElement.current) {
        previousActiveElement.current.focus()
      }
    }
  }, [])

  if (!session || session.resolved) return null

  return (
    <EventToast
      ref={envelopeRef}
      delivery={session.delivery}
      onOpen={handleOpen}
      onLater={handleDismiss}
      onClose={handleDismiss}
    />
  )
}