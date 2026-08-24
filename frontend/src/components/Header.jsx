import React from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'

export default function Header() {
  const location = useLocation()
  const navigate = useNavigate()
  const { user, signOut } = useAuth()

  // Do not show the top navigation bar on the dashboard page as it uses its own custom sidebar layout
  if (location.pathname === '/dashboard') {
    return null
  }

  const handleSignOut = () => {
    signOut()
    navigate('/')
  }

  return (
    <nav className="nav" aria-label="Main Navigation">
      <Link to="/" className="nav-logo" aria-label="BorrowBox Home">
        <span className="nav-logo-icon" aria-hidden="true">📦</span>
        <strong>BorrowBox</strong>
      </Link>
      
      <ul className="nav-links">
        <li><Link to="/">Home</Link></li>
        <li><a href="#how-it-works">How It Works</a></li>
        <li><a href="#about">About</a></li>
      </ul>

      <div className="nav-actions">
        {user ? (
          <>
            <button className="btn btn-ghost" onClick={() => navigate('/dashboard')}>
              Go to Workspace
            </button>
            <button className="btn btn-primary" onClick={handleSignOut}>
              Sign Out
            </button>
          </>
        ) : (
          <>
            <Link to="/signin" className="btn btn-ghost">Sign In</Link>
            <Link to="/signup" className="btn btn-primary">Sign Up</Link>
          </>
        )}
      </div>
    </nav>
  )
}
