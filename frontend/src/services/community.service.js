import { apiClient } from '../utils/apiClient'

function queryString(params) {
  const query = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&')
  return query ? `?${query}` : ''
}

export const communityService = {
  getCommunities: () => apiClient.get('/communities'),
  getCommunity: (id) => apiClient.get(`/communities/${id}`),
  createCommunity: (data) => apiClient.post('/communities', data),
  getMembers: (communityId, filters = {}) =>
    apiClient.get(`/communities/${communityId}/members${queryString(filters)}`),
  getPendingMembers: (communityId) => apiClient.get(`/communities/${communityId}/members/pending`),
  joinCommunity: (communityId, data = {}) => apiClient.post(`/communities/${communityId}/join`, data),
  leaveCommunity: (communityId) => apiClient.post(`/communities/${communityId}/leave`),
  getDashboard: (communityId) => apiClient.get(`/communities/${communityId}/dashboard`),
  getHealth: (communityId) => apiClient.get(`/communities/${communityId}/health`),
  suspendMember: (communityId, membershipId) =>
    apiClient.post(`/communities/${communityId}/members/${membershipId}/suspend`),
  reinstateMember: (communityId, membershipId) =>
    apiClient.post(`/communities/${communityId}/members/${membershipId}/reinstate`),
  removeMember: (communityId, membershipId) =>
    apiClient.post(`/communities/${communityId}/members/${membershipId}/remove`),
}