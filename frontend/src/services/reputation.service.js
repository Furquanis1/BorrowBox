import { apiClient } from '../utils/apiClient'

export const reputationService = {
  listForUser: (communityId = null) => {
    const params = communityId ? `?communityId=${communityId}` : ''
    return apiClient.get(`/me/reputation-events${params}`)
  }
}
