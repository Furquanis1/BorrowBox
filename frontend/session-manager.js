/**
 * BorrowBox session manager
 * Centralizes user and token persistence in localStorage.
 */

const SESSION_KEY = 'borrowbox.session';
const CURRENT_USER_KEY = 'currentUser';
const AUTH_TOKEN_KEY = 'authToken';

function readSession() {
  try {
    const rawSession = localStorage.getItem(SESSION_KEY);
    if (rawSession) {
      return JSON.parse(rawSession);
    }

    const currentUser = localStorage.getItem(CURRENT_USER_KEY);
    const authToken = localStorage.getItem(AUTH_TOKEN_KEY);
    if (!currentUser && !authToken) {
      return null;
    }

    return {
      user: currentUser ? JSON.parse(currentUser) : null,
      token: authToken || null
    };
  } catch {
    return null;
  }
}

function persistSession(session) {
  if (!session) {
    clearSession();
    return;
  }

  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  if (session.user) {
    localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(session.user));
  }
  if (session.token) {
    localStorage.setItem(AUTH_TOKEN_KEY, session.token);
  }
}

function getCurrentUser() {
  const session = readSession();
  return session?.user || null;
}

function getAuthToken() {
  const session = readSession();
  return session?.token || null;
}

function setSession(user, token) {
  persistSession({ user, token });
}

function clearSession() {
  localStorage.removeItem(SESSION_KEY);
  localStorage.removeItem(CURRENT_USER_KEY);
  localStorage.removeItem(AUTH_TOKEN_KEY);
}

function isAuthenticated() {
  return Boolean(getCurrentUser() && getAuthToken());
}

function requireAuth(redirectUrl = 'auth.html') {
  if (!isAuthenticated()) {
    window.location.href = redirectUrl;
    return false;
  }

  return true;
}