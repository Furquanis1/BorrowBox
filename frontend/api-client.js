/**
 * BorrowBox API Client
 * Handles all API requests to the backend
 */

const API_BASE_URL = 'http://localhost:8080/api';

class ApiClient {
    return this.request('/auth/register', {
    // Detect if running locally and adjust API base URL
    if (window.location.protocol === 'file:' || window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        this.baseUrl = 'http://localhost:8080/api';
      } else {

  async login(email, password) {
    return this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
  }
        // In production, use relative URL or adjust as needed
        this.baseUrl = '/api';
      }
  }

  /**
   * Get Authorization header
   */
  getAuthHeaders() {
    const headers = {
      'Content-Type': 'application/json'
    };
    const token = typeof getAuthToken === 'function' ? getAuthToken() : localStorage.getItem('authToken');
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    return headers;
  }

  /**
   * Set auth token after login
   */
  setToken(token) {
    const user = typeof getCurrentUser === 'function' ? getCurrentUser() : null;
    if (typeof setSession === 'function' && user) {
      setSession(user, token);
    } else {
      localStorage.setItem('authToken', token);
    }
  }

  /**
   * Clear auth token on logout
   */
  clearToken() {
    if (typeof clearSession === 'function') {
      clearSession();
    } else {
      localStorage.removeItem('authToken');
      localStorage.removeItem('currentUser');
    }
  }

  /**
   * Generic fetch wrapper with error handling
   */
  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;
    const response = await fetch(url, {
      ...options,
      headers: this.getAuthHeaders()
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.message || `API Error: ${response.status}`);
    }

    return response.json();
  }

  /**
   * USER ENDPOINTS
   */
  async createUser(fullName, email, password) {
    return this.request('/users', {
      method: 'POST',
      body: JSON.stringify({ fullName, email })
    });
  }

  async getUserById(id) {
    return this.request(`/users/${id}`);
  }

  async getAllUsers() {
    return this.request('/users');
  }

  async updateUser(id, fullName, email) {
    return this.request(`/users/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ fullName, email })
    });
  }

  /**
   * ITEM ENDPOINTS
   */
  async createItem(title, description, categoryId, condition = 'GOOD', visibility = 'GROUP') {
    return this.request('/items', {
      method: 'POST',
      body: JSON.stringify({ title, description, categoryId, condition, visibility })
    });
  }

  async getAllItems() {
    return this.request('/items');
  }

  async searchItems(query = '', status = '', categoryId = '', groupId = '', page = 0, size = 10) {
    const params = new URLSearchParams();
    if (query) params.append('q', query);
    if (status) params.append('status', status);
    if (categoryId) params.append('categoryId', categoryId);
    if (groupId) params.append('groupId', groupId);
    params.append('page', page);
    params.append('size', size);

    return this.request(`/items/search?${params.toString()}`);
  }

  async getItemById(id) {
    return this.request(`/items/${id}`);
  }

  async archiveItem(id) {
    return this.request(`/items/${id}/archive`, { method: 'POST' });
  }

  /**
   * GROUP ENDPOINTS
   */
  async createGroup(groupName, description = '') {
    return this.request('/groups', {
      method: 'POST',
      body: JSON.stringify({ groupName, description })
    });
  }

  async getAllGroups() {
    return this.request('/groups');
  }

  async getGroupById(id) {
    return this.request(`/groups/${id}`);
  }

  async updateGroup(id, groupName, description) {
    return this.request(`/groups/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ groupName, description })
    });
  }

  async deleteGroup(id) {
    return this.request(`/groups/${id}`, { method: 'DELETE' });
  }

  async addUserToGroup(groupId, userId) {
    return this.request(`/groups/${groupId}/users/${userId}`, { method: 'POST' });
  }

  /**
   * BORROW REQUEST ENDPOINTS
   */
  async createBorrowRequest(itemId, requestedByUserId, message = '') {
    return this.request('/borrow-requests', {
      method: 'POST',
      body: JSON.stringify({ itemId, requestedByUserId, message })
    });
  }

  async getAllBorrowRequests() {
    return this.request('/borrow-requests');
  }

  async getBorrowRequestById(id) {
    return this.request(`/borrow-requests/${id}`);
  }

  async updateBorrowRequest(id, status, requestedDeadline) {
    return this.request(`/borrow-requests/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ status, requestedDeadline })
    });
  }

  async approveBorrowRequest(id) {
    return this.request(`/borrow-requests/${id}/approve`, { method: 'POST' });
  }

  async deleteBorrowRequest(id) {
    return this.request(`/borrow-requests/${id}`, { method: 'DELETE' });
  }

  /**
   * BORROW RECORD ENDPOINTS
   */
  async createBorrowRecord(borrowRequestId, itemId, borrowedByUserId, dueAt) {
    return this.request('/borrow-records', {
      method: 'POST',
      body: JSON.stringify({ borrowRequestId, itemId, borrowedByUserId, dueAt })
    });
  }

  async searchBorrowRecords(active = false, overdue = false, page = 0, size = 10) {
    const params = new URLSearchParams();
    if (active) params.append('active', 'true');
    if (overdue) params.append('overdue', 'true');
    params.append('page', page);
    params.append('size', size);

    return this.request(`/borrow-records/search?${params.toString()}`);
  }

  /**
   * CATEGORY ENDPOINTS
   */
  async getAllCategories() {
    return this.request('/categories');
  }

  /**
   * HEALTH CHECK
   */
  async healthCheck() {
    return this.request('/health');
  }
}

// Create global instance
const api = new ApiClient();
