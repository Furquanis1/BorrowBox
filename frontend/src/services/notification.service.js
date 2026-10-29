import { apiClient } from '../utils/apiClient'

export const notificationService = {
  listNotifications: () => apiClient.get('/me/notifications'),
  getUnreadCount: () => apiClient.get('/me/notifications/unread-count'),
  markAsRead: (id) => apiClient.post(`/me/notifications/${id}/read`),
  listPreferences: () => apiClient.get('/me/notification-preferences'),
  setPreference: (type, enabled) =>
    apiClient.patch(`/me/notification-preferences/${type}`, enabled),
}
