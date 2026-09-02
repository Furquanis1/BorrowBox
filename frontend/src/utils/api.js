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

  // ─── Communities ────────────────────────────────────────
  getCommunities: () => request('/communities'),

  getCommunity: (id) => request(`/communities/${id}`),

  createCommunity: (name, description, type, admissionMode) =>
    request('/communities', {
      method: 'POST',
      body: JSON.stringify({ name, description, type, admissionMode }),
    }),

  getCommunityMembers: (id) => request(`/communities/${id}/members`),

  // ─── Memberships ────────────────────────────────────────
  getMyMemberships: () => request('/memberships'),
};
