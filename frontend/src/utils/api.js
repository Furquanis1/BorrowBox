/**
 * Centralised API client for BorrowBox.
 *
 * Every request is sent with `credentials: 'include'` so that the
 * browser attaches the HttpOnly `jwt` cookie automatically.
 */
const API_BASE = '/api';

/**
 * Thin wrapper around fetch that:
 *  1. Includes cookies (`credentials: 'include'`).
 *  2. Sets `Content-Type: application/json` for mutating verbs.
 *  3. Surfaces server error messages.
 */
async function request(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    credentials: 'include',
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers,
    },
  });

  // DELETE endpoints often return 204 No Content
  if (res.status === 204) return null;

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || body.message || res.statusText);
  }

  // Some endpoints may return empty body (e.g. logout)
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  // ─── Auth ────────────────────────────────────────────────
  login: (email, password) =>
    request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  register: (fullName, email, password) =>
    request('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ fullName, email, password }),
    }),

  logout: () =>
    request('/auth/logout', { method: 'POST' }),

  /** Verify current cookie session — returns user info or throws. */
  me: () => request('/auth/me'),

  // ─── Users ───────────────────────────────────────────────
  createUser: (fullName, email) =>
    request('/users', {
      method: 'POST',
      body: JSON.stringify({ fullName, email }),
    }),

  getUsers: () => request('/users'),

  getUser: (id) => request(`/users/${id}`),

  // ─── Groups ──────────────────────────────────────────────
  getGroups: () => request('/groups'),

  getGroup: (id) => request(`/groups/${id}`),

  createGroup: (name, description) =>
    request('/groups', {
      method: 'POST',
      body: JSON.stringify({ name, description }),
    }),

  // ─── Items ───────────────────────────────────────────────
  getItems: () => request('/items'),

  searchItems: (query = '', status = '', categoryId = null, page = 0, size = 20, groupId = null, ownerId = null) => {
    const params = new URLSearchParams();
    if (query) params.append('q', query);
    if (status) params.append('status', status);
    if (categoryId) params.append('categoryId', categoryId);
    if (groupId) params.append('groupId', groupId);
    if (ownerId) params.append('ownerId', ownerId);
    params.append('page', page);
    params.append('size', size);
    return request(`/items/search?${params}`);
  },

  getUserItems: (userId) => {
    return request(`/items/search?ownerId=${userId}&size=100`);
  },

  getItem: (id) => request(`/items/${id}`),

  createItem: (title, description = null) =>
    request('/items', {
      method: 'POST',
      body: JSON.stringify({ title, description }),
    }),

  updateItem: (id, title, description) =>
    request(`/items/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ title, description }),
    }),

  deleteItem: (id) => request(`/items/${id}`, { method: 'DELETE' }),

  archiveItem: (id) => request(`/items/${id}/archive`, { method: 'POST' }),

  // ─── Borrow Requests ────────────────────────────────────
  getBorrowRequests: () => request('/borrow-requests'),

  getBorrowRequest: (id) => request(`/borrow-requests/${id}`),

  createBorrowRequest: (itemId, requestedByUserId, message = '') =>
    request('/borrow-requests', {
      method: 'POST',
      body: JSON.stringify({ itemId, requestedByUserId, message }),
    }),

  approveBorrowRequest: (id) =>
    request(`/borrow-requests/${id}/approve`, { method: 'POST' }),

  rejectBorrowRequest: (id) =>
    request(`/borrow-requests/${id}/reject`, { method: 'POST' }),

  /** Approve + auto-create BorrowRecord in one call. dueAt is ISO-8601 string. */
  confirmBorrowRequest: (id, dueAt) =>
    request(`/borrow-requests/${id}/confirm`, {
      method: 'POST',
      body: JSON.stringify({ dueAt }),
    }),

  // ─── Borrow Records ─────────────────────────────────────
  getBorrowRecords: () => request('/borrow-records'),

  searchBorrowRecords: (active = null, overdue = null, page = 0, size = 20) => {
    const params = new URLSearchParams();
    if (active !== null) params.append('active', active);
    if (overdue !== null) params.append('overdue', overdue);
    params.append('page', page);
    params.append('size', size);
    return request(`/borrow-records/search?${params}`);
  },

  getBorrowRecord: (id) => request(`/borrow-records/${id}`),

  createBorrowRecord: (borrowRequestId, itemId, borrowedByUserId, borrowedAt, dueAt) =>
    request('/borrow-records', {
      method: 'POST',
      body: JSON.stringify({ borrowRequestId, itemId, borrowedByUserId, borrowedAt, dueAt }),
    }),

  returnBorrowRecord: (id) =>
    request(`/borrow-records/${id}/return`, { method: 'POST' }),

  deleteBorrowRecord: (id) =>
    request(`/borrow-records/${id}`, { method: 'DELETE' }),
};
