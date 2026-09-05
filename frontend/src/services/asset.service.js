import { apiClient } from '../utils/apiClient'

export const assetService = {
  listAssets: () => apiClient.get('/assets'),
  getAsset: (id) => apiClient.get(`/assets/${id}`),
  createAsset: (data) => apiClient.post('/assets', data),
  archiveAsset: (id) => apiClient.patch(`/assets/${id}/archive`),
}