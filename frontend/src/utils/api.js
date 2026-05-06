const API_BASE = '/api';

export const api = {
  // Users
  createUser: (fullName, email) =>
    fetch(`${API_BASE}/users`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fullName, email })
    }).then(r => r.json()),

  getUsers: () =>
    fetch(`${API_BASE}/users`).then(r => r.json()),

  getUser: (id) =>
    fetch(`${API_BASE}/users/${id}`).then(r => r.json()),

  // Groups
  getGroups: () =>
    fetch(`${API_BASE}/groups`).then(r => r.json()),

  getGroup: (id) =>
    fetch(`${API_BASE}/groups/${id}`).then(r => r.json()),

  createGroup: (name, description) =>
    fetch(`${API_BASE}/groups`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, description })
    }).then(r => r.json()),

  // Items
  getItems: () =>
    fetch(`${API_BASE}/items`).then(r => r.json()),

  searchItems: (query = '', status = '', categoryId = null, page = 0, size = 20) => {
    const params = new URLSearchParams();
    if (query) params.append('q', query);
    if (status) params.append('status', status);
    if (categoryId) params.append('categoryId', categoryId);
    params.append('page', page);
    params.append('size', size);
    return fetch(`${API_BASE}/items/search?${params}`).then(r => r.json());
  },

  getItem: (id) =>
    fetch(`${API_BASE}/items/${id}`).then(r => r.json()),

  createItem: (title, description = null) =>
    fetch(`${API_BASE}/items`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, description })
    }).then(r => r.json()),

  updateItem: (id, title, description) =>
    fetch(`${API_BASE}/items/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, description })
    }).then(r => r.json()),

  deleteItem: (id) =>
    fetch(`${API_BASE}/items/${id}`, { method: 'DELETE' }).then(r => r.json()),

  archiveItem: (id) =>
    fetch(`${API_BASE}/items/${id}/archive`, { method: 'POST' }).then(r => r.json()),

  // Borrow Requests
  getBorrowRequests: () =>
    fetch(`${API_BASE}/borrow-requests`).then(r => r.json()),

  getBorrowRequest: (id) =>
    fetch(`${API_BASE}/borrow-requests/${id}`).then(r => r.json()),

  createBorrowRequest: (itemId, requestedByUserId, message = '') =>
    fetch(`${API_BASE}/borrow-requests`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ itemId, requestedByUserId, message })
    }).then(r => r.json()),

  approveBorrowRequest: (id) =>
    fetch(`${API_BASE}/borrow-requests/${id}/approve`, { method: 'POST' }).then(r => r.json()),

  rejectBorrowRequest: (id) =>
    fetch(`${API_BASE}/borrow-requests/${id}`, { method: 'DELETE' }).then(r => r.json()),

  // Borrow Records
  getBorrowRecords: () =>
    fetch(`${API_BASE}/borrow-records`).then(r => r.json()),

  searchBorrowRecords: (active = null, overdue = null, page = 0, size = 20) => {
    const params = new URLSearchParams();
    if (active !== null) params.append('active', active);
    if (overdue !== null) params.append('overdue', overdue);
    params.append('page', page);
    params.append('size', size);
    return fetch(`${API_BASE}/borrow-records/search?${params}`).then(r => r.json());
  },

  getBorrowRecord: (id) =>
    fetch(`${API_BASE}/borrow-records/${id}`).then(r => r.json()),

  createBorrowRecord: (borrowRequestId, itemId, borrowedByUserId, borrowedAt, dueAt) =>
    fetch(`${API_BASE}/borrow-records`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ borrowRequestId, itemId, borrowedByUserId, borrowedAt, dueAt })
    }).then(r => r.json()),

  returnBorrowRecord: (id) =>
    fetch(`${API_BASE}/borrow-records/${id}/return`, { method: 'POST' }).then(r => r.json()),

  deleteBorrowRecord: (id) =>
    fetch(`${API_BASE}/borrow-records/${id}`, { method: 'DELETE' }).then(r => r.json())
};
