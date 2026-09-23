import { apiClient } from '../utils/apiClient'

function queryString(params) {
  const query = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&')
  return query ? `?${query}` : ''
}

export const flagService = {
  listFlags: (communityId, filters = {}) =>
    apiClient.get(`/communities/${communityId}/flags${queryString(filters)}`),
  getFlag: (communityId, flagId) =>
    apiClient.get(`/communities/${communityId}/flags/${flagId}`),
  createFlag: (communityId, { flagType, transactionId, note }) =>
    apiClient.post(`/communities/${communityId}/flags`, {
      flagType,
      transactionId: transactionId || null,
      note: note || null,
    }),
  updateFlag: (communityId, flagId, updates) =>
    apiClient.patch(`/communities/${communityId}/flags/${flagId}`, updates),
}