import { apiClient } from '../utils/apiClient'

export const categoryService = {
  getCategories: () => apiClient.get('/categories'),
  getCategory: (id) => apiClient.get(`/categories/${id}`),
  createCategory: (data) => apiClient.post('/categories', data),
}