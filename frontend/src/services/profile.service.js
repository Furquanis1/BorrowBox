import { apiClient } from '../utils/apiClient'

export const profileService = {
  getTrustProfile: (communityId = null) => {
    const params = communityId ? `?communityId=${communityId}` : ''
    return apiClient.get(`/me/trust-profile${params}`)
  }
}
