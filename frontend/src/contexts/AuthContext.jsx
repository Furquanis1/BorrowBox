import React, { createContext, useState, useEffect } from 'react'
import { api } from '../utils/api'

export const AuthContext = createContext()

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [groups, setGroups] = useState([])
  const [activeGroup, setActiveGroup] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    const savedUser = localStorage.getItem('currentUser')
    const savedGroupId = localStorage.getItem('activeGroupId')
    if (savedUser) {
      setUser(JSON.parse(savedUser))
      loadGroups()
      if (savedGroupId) setActiveGroup(savedGroupId)
    }
  }, [])

  const loadGroups = async () => {
    try {
      const groupsList = await api.getGroups()
      setGroups(groupsList)
      if (groupsList.length > 0 && !activeGroup) {
        setActiveGroup(groupsList[0].id)
        localStorage.setItem('activeGroupId', groupsList[0].id)
      }
    } catch (err) {
      console.error('Failed to load groups:', err)
    }
  }

  const signUp = async (fullName, email, password) => {
    setLoading(true)
    try {
      const response = await api.register(fullName, email, password)
      const registeredUser = response.user
      setUser(registeredUser)
      localStorage.setItem('currentUser', JSON.stringify(registeredUser))
      await loadGroups()
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
      const response = await api.login(email, password)
      const loggedInUser = response.user
      setUser(loggedInUser)
      localStorage.setItem('currentUser', JSON.stringify(loggedInUser))
      await loadGroups()
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
      await api.logout()
    } catch (err) {
      console.error('Sign out on server failed, clearing local state anyway', err)
    }
    setUser(null)
    setActiveGroup(null)
    localStorage.removeItem('currentUser')
    localStorage.removeItem('activeGroupId')
  }

  const switchGroup = (groupId) => {
    setActiveGroup(groupId)
    localStorage.setItem('activeGroupId', groupId)
  }

  return (
    <AuthContext.Provider value={{
      user,
      groups,
      activeGroup,
      loading,
      signUp,
      signIn,
      signOut,
      switchGroup,
      loadGroups
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
