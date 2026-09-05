import React, { createContext, useState, useEffect } from 'react'
import { authService } from '../services'

export const AuthContext = createContext()

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(false)
  const [initializingAuth, setInitializingAuth] = useState(true) // start true — checking cookie

  /**
   * On mount, try to restore the session:
   *  1. Call GET /api/auth/me  (cookie is sent automatically).
   *  2. If the server confirms, use the returned user.
   *  3. If it fails (401 / network), clear any stale localStorage.
   */
  useEffect(() => {
    authService.me()
      .then(data => {
        const u = data?.user ?? data
        setUser(u)
        localStorage.setItem('currentUser', JSON.stringify(u))
      })
      .catch(() => {
        // Cookie expired or missing — clear stale local cache
        setUser(null)
        localStorage.removeItem('currentUser')
        localStorage.removeItem('activeCommunityId')
      })
      .finally(() => setInitializingAuth(false))
  }, [])

  const signUp = async (fullName, email, password) => {
    setLoading(true)
    try {
      const response = await authService.register(fullName, email, password)
      const registeredUser = response.user
      setUser(registeredUser)
      localStorage.setItem('currentUser', JSON.stringify(registeredUser))
      return registeredUser
    } catch (err) {
      console.error('Sign up failed:', err)
      throw err
    } finally {
      setLoading(false)
    }
  }

  const signIn = async (email, password) => {
    setLoading(true)
    try {
      const response = await authService.login(email, password)
      const loggedInUser = response.user
      setUser(loggedInUser)
      localStorage.setItem('currentUser', JSON.stringify(loggedInUser))
      return loggedInUser
    } catch (err) {
      console.error('Sign in failed:', err)
      throw err
    } finally {
      setLoading(false)
    }
  }

  const signOut = async () => {
    try {
      await authService.logout()
    } catch (err) {
      console.error('Sign out on server failed, clearing local state anyway', err)
    }
    setUser(null)
    localStorage.removeItem('currentUser')
    localStorage.removeItem('activeCommunityId')
  }

  return (
    <AuthContext.Provider value={{
      user,
      loading,
      initializingAuth,
      signUp,
      signIn,
      signOut
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = React.useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}