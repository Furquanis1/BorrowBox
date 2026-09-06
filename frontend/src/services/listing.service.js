import { apiClient } from '../utils/apiClient'

export const listingService = {
  getAssetListings: (assetId) => apiClient.get(`/assets/${assetId}/listings`),
  createListing: (assetId, data) => apiClient.post(`/assets/${assetId}/listings`, data),
  unlist: (assetId, communityId) => apiClient.del(`/assets/${assetId}/listings/${communityId}`),
  getCommunityListings: (communityId) => apiClient.get(`/communities/${communityId}/listings`),
}