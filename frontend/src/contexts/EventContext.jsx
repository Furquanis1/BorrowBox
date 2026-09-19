import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'

const EventContext = createContext()

export function EventProvider({ children }) {
  const [deliveries, setDeliveries] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const fetchDeliveries = useCallback(async (statuses = null) => {
    setLoading(true)
    setError(null)
    try {
      let url = '/api/me/events'
      if (statuses && statuses.length > 0) {
        url += '?status=' + statuses.join(',')
      }
      const response = await fetch(url, { credentials: 'include' })
      if (!response.ok) throw new Error('Failed to fetch events')
      const data = await response.json()
      setDeliveries(data)
    } catch (err) {
      setError(err.message)
      setDeliveries([])
    } finally {
      setLoading(false)
    }
  }, [])

  const unreadCount = deliveries.filter(d => d.status === 'UNREAD').length

  const markRead = useCallback(async (deliveryId) => {
    try {
      const response = await fetch(`/api/me/events/${deliveryId}/read`, {
        method: 'POST',
        credentials: 'include'
      })
      if (!response.ok) throw new Error('Failed to mark read')
      const updated = await response.json()
      setDeliveries(deliveries.map(d => d.deliveryId === deliveryId ? updated : d))
    } catch (err) {
      console.error('markRead failed:', err)
    }
  }, [deliveries])

  const dismiss = useCallback(async (deliveryId) => {
    try {
      const response = await fetch(`/api/me/events/${deliveryId}/dismiss`, {
        method: 'POST',
        credentials: 'include'
      })
      if (!response.ok) throw new Error('Failed to dismiss')
      const updated = await response.json()
      setDeliveries(deliveries.map(d => d.deliveryId === deliveryId ? updated : d))
    } catch (err) {
      console.error('dismiss failed:', err)
    }
  }, [deliveries])

  const markAllRead = useCallback(async () => {
    try {
      const response = await fetch('/api/me/events/read-all', {
        method: 'POST',
        credentials: 'include'
      })
      if (!response.ok) throw new Error('Failed to mark all read')
      setDeliveries(deliveries.map(d =>
        d.status === 'UNREAD' ? { ...d, status: 'READ', readAt: new Date().toISOString() } : d
      ))
    } catch (err) {
      console.error('markAllRead failed:', err)
    }
  }, [deliveries])

  // Refresh on window focus
  useEffect(() => {
    const handleFocus = () => fetchDeliveries()
    window.addEventListener('focus', handleFocus)
    return () => window.removeEventListener('focus', handleFocus)
  }, [fetchDeliveries])

  // Initial load
  useEffect(() => {
    fetchDeliveries()
  }, [fetchDeliveries])

  return (
    <EventContext.Provider value={{
      deliveries,
      loading,
      error,
      unreadCount,
      fetchDeliveries,
      markRead,
      dismiss,
      markAllRead
    }}>
      {children}
    </EventContext.Provider>
  )
}

export function useEvents() {
  const context = useContext(EventContext)
  if (!context) {
    throw new Error('useEvents must be used within an EventProvider')
  }
  return context
}