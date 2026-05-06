import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'

export default function SignUpPage() {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const { signUp, loading } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      await signUp(fullName, email)
      navigate('/dashboard')
    } catch (err) {
      setError(err.message || 'Sign up failed')
    }
  }

  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-logo">📦 BorrowBox</div>
        <h2 className="auth-title">Create Your Account</h2>
        <p className="auth-subtitle">Join your community and start sharing</p>
        
        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group">
            <label>Full Name</label>
            <input
              type="text"
              placeholder="Alex Johnson"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              required
            />
          </div>
          
          <div className="form-group">
            <label>Email</label>
            <input
              type="email"
              placeholder="alex@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          {error && <div className="error-message">{error}</div>}

          <button type="submit" className="btn btn-primary btn-lg" style={{width:'100%'}} disabled={loading}>
            {loading ? 'Creating account...' : 'Create Account'}
          </button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to="/signin" className="auth-link">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
