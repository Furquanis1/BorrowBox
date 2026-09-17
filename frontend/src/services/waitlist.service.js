import { apiClient } from '../utils/apiClient'

export const waitlistService = {
  join: (listingId, purpose, requestedDurationDays) =>
    apiClient.post(`/listings/${listingId}/waitlist`, { purpose, requestedDurationDays }),
  getMine: () => apiClient.get('/me/waitlist'),
  leave: (entryId) => apiClient.del(`/me/waitlist/${entryId}`),
}
