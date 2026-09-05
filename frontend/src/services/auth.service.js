import { apiClient } from '../utils/apiClient'

export const authService = {
  login: (email, password) => apiClient.post('/auth/login', { email, password }),
  register: (fullName, email, password) => apiClient.post('/auth/register', { fullName, email, password }),
  logout: () => apiClient.post('/auth/logout'),
  me: () => apiClient.get('/auth/me'),
  getUsers: () => apiClient.get('/users'),
  getUser: (id) => apiClient.get(`/users/${id}`),
  createUser: (fullName, email, password) => apiClient.post('/users', { fullName, email, password }),
}