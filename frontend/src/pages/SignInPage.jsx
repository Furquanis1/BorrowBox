import React, { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { api } from '../utils/api'

export default function SignInPage() {
  const [users, setUsers] = useState([])
  const [selectedUserId, setSelectedUserId] = useState('')
  const [error, setError] = useState('')
  const { signIn, loading } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    loadUsers()
  }, [])

  const loadUsers = async () => {
    try {
      const usersList = await api.getUsers()
      setUsers(usersList)
    } catch (err) {
      setError('Failed to load users')
    }
  }

  const handleSignIn = async (e) => {
    e.preventDefault()
    if (!selectedUserId) {
      setError('Please select a user')
      return
    }
    try {
      await signIn(parseInt(selectedUserId))
      navigate('/dashboard')
    } catch (err) {
      setError(err.message || 'Sign in failed')
    }
  }

  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-logo">📦 BorrowBox</div>
        <h2 className="auth-title">Welcome Back</h2>
        <p className="auth-subtitle">Select your account to continue</p>
        
        <form onSubmit={handleSignIn} className="auth-form">
          <div className="form-group">
            <label>Select Your Account</label>
            <select
              value={selectedUserId}
              onChange={(e) => setSelectedUserId(e.target.value)}
              required
            >
              <option value="">Choose an account...</option>
              {users.map(user => (
                <option key={user.id} value={user.id}>
                  {user.fullName} ({user.email})
                </option>
              ))}
            </select>
          </div>

          {error && <div className="error-message">{error}</div>}

          <button type="submit" className="btn btn-primary btn-lg" style={{width:'100%'}} disabled={loading}>
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <p className="auth-footer">
          Don't have an account? <Link to="/signup" className="auth-link">Create one</Link>
        </p>
      </div>
    </div>
  )
}
