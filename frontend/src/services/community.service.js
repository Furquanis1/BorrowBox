import { apiClient } from '../utils/apiClient'

export const communityService = {
  getCommunities: () => apiClient.get('/communities'),
  getCommunity: (id) => apiClient.get(`/communities/${id}`),
  createCommunity: (data) => apiClient.post('/communities', data),
  getMembers: (communityId) => apiClient.get(`/communities/${communityId}/members`),
  getPendingMembers: (communityId) => apiClient.get(`/communities/${communityId}/members/pending`),
  joinCommunity: (communityId, data = {}) => apiClient.post(`/communities/${communityId}/join`, data),
  leaveCommunity: (communityId) => apiClient.post(`/communities/${communityId}/leave`),
}