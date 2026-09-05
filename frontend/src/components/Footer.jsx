import React from 'react'
import { Link } from 'react-router-dom'

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="site-footer-grid">
        <div>
          <div className="site-footer-brand">
            <span className="site-footer-logo" aria-hidden="true">
              <i className="bi bi-box-seam" />
            </span>
            BorrowBox
          </div>
          <p className="site-footer-desc">
            A community-led lending platform designed to share assets responsibly and reduce waste.
          </p>
        </div>
        <div>
          <h4>Platform</h4>
          <ul>
            <li>
              <Link to="/">Home</Link>
            </li>
            <li>
              <a href="#how-it-works">How It Works</a>
            </li>
          </ul>
        </div>
        <div>
          <h4>Security</h4>
          <p className="site-footer-desc">
            Built using Spring Security, encrypted tokens, and secure storage mechanisms.
          </p>
        </div>
      </div>
      <div className="site-footer-bottom">
        &copy; {new Date().getFullYear()} BorrowBox. All rights reserved.
      </div>
    </footer>
  )
}