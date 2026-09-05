import { apiClient } from '../utils/apiClient'

export const ruleService = {
  listRules: (communityId) => apiClient.get(`/communities/${communityId}/rules`),
  listActiveRules: (communityId) => apiClient.get(`/communities/${communityId}/rules/active`),
  createRule: (communityId, ruleType, value) => apiClient.post(`/communities/${communityId}/rules`, { ruleType, value }),
  updateRule: (communityId, ruleId, ruleType, value) => apiClient.patch(`/communities/${communityId}/rules/${ruleId}`, { ruleType, value }),
  activateRule: (communityId, ruleId) => apiClient.post(`/communities/${communityId}/rules/${ruleId}/activate`),
  deactivateRule: (communityId, ruleId) => apiClient.post(`/communities/${communityId}/rules/${ruleId}/deactivate`),
}