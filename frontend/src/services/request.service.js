import { apiClient } from '../utils/apiClient'

export const requestService = {
  create: (listingId, purpose, requestedDurationDays) =>
    apiClient.post('/transactions', { listingId, purpose, requestedDurationDays }),
  getMine: () => apiClient.get('/me/requests'),
  getLendRequests: () => apiClient.get('/me/lend-requests'),
  get: (id) => apiClient.get(`/transactions/${id}`),
  approve: (id, note) => apiClient.post(`/transactions/${id}/approve`, note ? { note } : undefined),
  reject: (id, note) => apiClient.post(`/transactions/${id}/reject`, note ? { note } : undefined),
  counterOffer: (id, data) => apiClient.post(`/transactions/${id}/counter-offer`, data),
  acceptCounter: (id) => apiClient.post(`/transactions/${id}/accept-counter`),
  cancel: (id) => apiClient.post(`/transactions/${id}/cancel`),
  stageHandover: (id) => apiClient.post(`/transactions/${id}/stage-handover`),
  confirmHandover: (id) => apiClient.post(`/transactions/${id}/confirm-handover`),
  initiateReturn: (id) => apiClient.post(`/transactions/${id}/initiate-return`),
  reportHandback: (id) => apiClient.post(`/transactions/${id}/report-handback`),
  confirmReturn: (id) => apiClient.post(`/transactions/${id}/confirm-return`),
  getMessages: (id) => apiClient.get(`/transactions/${id}/messages`),
  sendMessage: (id, body) => apiClient.post(`/transactions/${id}/messages`, { body }),
}