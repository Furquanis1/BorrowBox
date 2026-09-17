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
  confirmReceipt: (id) => apiClient.post(`/transactions/${id}/confirm-receipt`),
  disputeHandover: (id) => apiClient.post(`/transactions/${id}/dispute-handover`),
  initiateReturn: (id) => apiClient.post(`/transactions/${id}/initiate-return`),
  reportHandback: (id) => apiClient.post(`/transactions/${id}/report-handback`),
  confirmReturn: (id) => apiClient.post(`/transactions/${id}/confirm-return`),
  disputeReturn: (id) => apiClient.post(`/transactions/${id}/dispute-return`),
  uploadEvidence: (id, type, file) => {
    const form = new FormData()
    form.append('type', type)
    form.append('file', file)
    return apiClient.multipart(`/transactions/${id}/evidence`, form)
  },
  getEvidence: (id) => apiClient.get(`/transactions/${id}/evidence`),
  getEvidenceContentUrl: (evidenceId) => `/api/evidence/${evidenceId}/content`,
  requestExtension: (id, data) => apiClient.post(`/transactions/${id}/extension-request`, data),
  acceptExtension: (id) => apiClient.post(`/transactions/${id}/extension-accept`),
  rejectExtension: (id) => apiClient.post(`/transactions/${id}/extension-reject`),
  counterExtension: (id, data) => apiClient.post(`/transactions/${id}/extension-counter`, data),
  acceptExtensionCounter: (id) => apiClient.post(`/transactions/${id}/extension-accept-counter`),
  rejectExtensionCounter: (id) => apiClient.post(`/transactions/${id}/extension-counter-reject`),
  getMessages: (id) => apiClient.get(`/transactions/${id}/messages`),
  sendMessage: (id, body) => apiClient.post(`/transactions/${id}/messages`, { body }),
}