import { apiClient } from '../utils/apiClient'

export const eventService = {
  getMine: (statuses = null) => {
    const params = statuses ? `?status=${statuses.join(',')}` : ''
    return apiClient.get(`/me/events${params}`)
  },

  getByTransaction: (transactionId) =>
    apiClient.get(`/transactions/${transactionId}/events`),

  markRead: (deliveryId) =>
    apiClient.post(`/me/events/${deliveryId}/read`),

  dismiss: (deliveryId) =>
    apiClient.post(`/me/events/${deliveryId}/dismiss`),

  markAllRead: () =>
    apiClient.post('/me/events/read-all')
}