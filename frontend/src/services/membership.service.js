import { apiClient } from '../utils/apiClient'

export const membershipService = {
  getMyMemberships: () => apiClient.get('/memberships'),
  decideMembership: (membershipId, decision) => apiClient.post(`/memberships/${membershipId}/decision`, { decision }),
}