import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import Input from '../components/ui/Input'
import Button from '../components/ui/Button'

export default function SignUpPage() {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const { signUp, loading } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      await signUp(fullName, email, password)
      navigate('/dashboard')
    } catch (err) {
      setError(err.message || 'Sign up failed')
    }
  }

  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-logo">
          <i className="bi bi-box-seam auth-logo-icon" aria-hidden="true" />
          BorrowBox
        </div>
        <h2 className="auth-title">Create Your Account</h2>
        <p className="auth-subtitle">Join your community and start sharing</p>

        <form onSubmit={handleSubmit} className="auth-form" aria-label="Create Account Form">
          <Input
            id="signup-name"
            label="Full Name"
            type="text"
            placeholder="Alex Johnson"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            autoComplete="name"
            required
          />

          <Input
            id="signup-email"
            label="Email Address"
            type="email"
            placeholder="alex@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />

          <Input
            id="signup-password"
            label="Password"
            type="password"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
          />

          {error && (
            <div className="error-message" role="alert">
              {error}
            </div>
          )}

          <Button type="submit" block size="lg" className="auth-submit" loading={loading}>
            {loading ? 'Creating account...' : 'Create Account'}
          </Button>
        </form>

        <p className="auth-footer">
          Already have an account?{' '}
          <Link to="/signin" className="auth-link">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}