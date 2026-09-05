/**
 * Low-level BorrowBox API client.
 *
 * Every request is sent with `credentials: 'include'` so that the
 * browser attaches the HttpOnly `jwt` cookie automatically.
 *
 * Response behaviour intentionally mirrors the previous centralised client:
 *  - 204 -> null
 *  - non-ok -> throws ApiError whose `.message` is the backend `error`/`message`
 *    field (or the HTTP status text), so existing UI error handling is unchanged.
 */
const API_BASE = '/api';

export class ApiError extends Error {
  constructor(message, { status, kind, fields } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.kind = kind
    this.fields = fields
  }
}

function errorKind(status, body) {
  if (status === 401) return 'unauthorized'
  if (status === 403) return 'forbidden'
  if (status === 404) return 'not-found'
  // A field-keyed validation body (no `error` key) is a 400 validation error.
  if (status === 400 && body && !body.error) return 'validation'
  if (status >= 400 && status < 500) return 'client'
  if (status >= 500) return 'server'
  return 'error'
}

async function request(path, options = {}) {
  let res
  try {
    res = await fetch(`${API_BASE}${path}`, {
      ...options,
      credentials: 'include',
      headers: {
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
    })
  } catch (err) {
    throw new ApiError(err.message || 'Network error', { kind: 'network' })
  }

  // DELETE endpoints often return 204 No Content
  if (res.status === 204) return null

  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new ApiError(body.error || body.message || res.statusText, {
      status: res.status,
      kind: errorKind(res.status, body),
      fields: body && body.error ? undefined : body,
    })
  }

  // Some endpoints may return an empty body (e.g. logout)
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export const apiClient = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body: body !== undefined ? JSON.stringify(body) : undefined }),
  patch: (path, body) => request(path, { method: 'PATCH', body: body !== undefined ? JSON.stringify(body) : undefined }),
  put: (path, body) => request(path, { method: 'PUT', body: body !== undefined ? JSON.stringify(body) : undefined }),
  del: (path) => request(path, { method: 'DELETE' }),
}

export default apiClient