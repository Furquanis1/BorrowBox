import React from 'react'
import { useLocation } from 'react-router-dom'

export default function Footer() {
  const location = useLocation()

  // Do not show footer on the dashboard workspace page to keep it clean and fits the screen height
  if (location.pathname === '/dashboard') {
    return null
  }

  return (
    <footer className="footer" style={{
      background: 'var(--forest)',
      color: 'white',
      padding: '48px 5% 24px',
      marginTop: 'auto',
      borderTop: '1px solid rgba(255, 255, 255, 0.1)'
    }}>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
        gap: '32px',
        maxWidth: '1200px',
        margin: '0 auto 32px'
      }}>
        <div>
          <h3 style={{
            fontSize: '1.25rem',
            color: 'white',
            marginBottom: '16px',
            fontFamily: 'DM Serif Display, serif'
          }}>📦 BorrowBox</h3>
          <p style={{
            color: 'var(--mist)',
            fontSize: '0.875rem',
            lineHeight: '1.6'
          }}>
            A full-stack community-led lending platform designed to share assets responsibly and reduce waste.
          </p>
        </div>
        <div>
          <h4 style={{ color: 'var(--mint)', marginBottom: '16px', fontSize: '0.9rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Platform</h4>
          <ul style={{ listStyle: 'none', padding: 0 }}>
            <li style={{ marginBottom: '8px' }}><a href="/" style={{ color: 'var(--foam)', textDecoration: 'none', fontSize: '0.875rem' }}>Home</a></li>
            <li style={{ marginBottom: '8px' }}><a href="#how-it-works" style={{ color: 'var(--foam)', textDecoration: 'none', fontSize: '0.875rem' }}>How It Works</a></li>
          </ul>
        </div>
        <div>
          <h4 style={{ color: 'var(--mint)', marginBottom: '16px', fontSize: '0.9rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Security</h4>
          <p style={{ color: 'var(--mist)', fontSize: '0.875rem', lineHeight: '1.6' }}>
            Built using Spring Security, encrypted tokens, and secure storage mechanisms.
          </p>
        </div>
      </div>
      <div style={{
        textAlign: 'center',
        borderTop: '1px solid rgba(255, 255, 255, 0.1)',
        paddingTop: '24px',
        color: 'var(--mist)',
        fontSize: '0.825rem'
      }}>
        &copy; {new Date().getFullYear()} BorrowBox. All rights reserved.
      </div>
    </footer>
  )
}
