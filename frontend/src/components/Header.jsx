import React from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import Button from './ui/Button'

export default function Header() {
  const navigate = useNavigate()
  const { user, signOut } = useAuth()

  const handleSignOut = async () => {
    await signOut()
    navigate('/')
  }

  return (
    <nav className="nav" aria-label="Main Navigation">
      <Link to="/" className="nav-logo" aria-label="BorrowBox Home">
        <span className="nav-logo-icon">
          <i className="bi bi-box-seam" aria-hidden="true" />
        </span>
        <strong>BorrowBox</strong>
      </Link>

      <ul className="nav-links">
        <li>
          <Link to="/">Home</Link>
        </li>
        <li>
          <a href="#how-it-works">How It Works</a>
        </li>
      </ul>

      <div className="nav-actions">
        {user ? (
          <>
            <Button as={Link} to="/dashboard" variant="ghost">
              Go to Workspace
            </Button>
            <Button variant="outline" onClick={handleSignOut}>
              Sign Out
            </Button>
          </>
        ) : (
          <>
            <Button as={Link} to="/signin" variant="ghost">
              Sign In
            </Button>
            <Button as={Link} to="/signup" variant="primary">
              Sign Up
            </Button>
          </>
        )}
      </div>
    </nav>
  )
}