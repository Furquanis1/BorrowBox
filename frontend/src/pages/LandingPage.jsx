import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'

export default function LandingPage() {
  const navigate = useNavigate()
  const { user } = useAuth()

  const handleEnter = () => {
    if (user) {
      navigate('/dashboard')
    } else {
      navigate('/signin')
    }
  }
  const [stats, setStats] = useState({
    items: null,
    loans: null,
    requests: null,
    loading: true,
    error: null
  })

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const [itemsRes, loansRes, requestsRes] = await Promise.all([
          fetch('/api/items/search?size=1&page=0'),
          fetch('/api/borrow-records/search?size=1&page=0'),
          fetch('/api/borrow-requests')
        ])

        if (!itemsRes.ok || !loansRes.ok || !requestsRes.ok) {
          throw new Error('Failed to fetch stats')
        }

        const itemsData = await itemsRes.json()
        const loansData = await loansRes.json()
        const requestsData = await requestsRes.json()

        setStats({
          items: itemsData.totalElements || 0,
          loans: loansData.totalElements || 0,
          requests: Array.isArray(requestsData) ? requestsData.length : 0,
          loading: false,
          error: null
        })
      } catch (err) {
        console.error('Failed to load stats:', err)
        setStats(prev => ({
          ...prev,
          loading: false,
          error: 'Unable to load data. Please check the backend connection.'
        }))
      }
    }

    fetchStats()
  }, [])

  return (
    <div className="landing-page">

      {/* Hero Section */}
      <section className="landing-hero" aria-labelledby="hero-heading">
        <div className="landing-hero-content">
          <h1 id="hero-heading" style={{ fontSize: 'clamp(2.5rem, 5vw, 4rem)', fontWeight: 800, color: 'var(--forest)', letterSpacing: '-1.5px', marginBottom: '16px', lineHeight: 1.15 }}>
            Manage Borrowing &amp; Lending Simply
          </h1>
          <p className="landing-subheading">
            Track items, manage borrow requests, and keep everyone on the same page.
          </p>
          <button className="landing-primary-btn" onClick={handleEnter}>
            Start Now
          </button>
        </div>
      </section>

      {/* Stats Section */}
      <section className="landing-stats" aria-labelledby="stats-heading">
        <div className="landing-stats-content">
          <h2 id="stats-heading">What's in BorrowBox</h2>
          <div className="stats-grid" role="region" aria-label="Community Statistics">
            <div className="stat-card">
              <div className="stat-number" aria-live="polite">
                {stats.loading ? '—' : stats.error ? '?' : stats.items}
              </div>
              <div className="stat-label">Items Available</div>
            </div>
            <div className="stat-card">
              <div className="stat-number" aria-live="polite">
                {stats.loading ? '—' : stats.error ? '?' : stats.loans}
              </div>
              <div className="stat-label">Active Loans</div>
            </div>
            <div className="stat-card">
              <div className="stat-number" aria-live="polite">
                {stats.loading ? '—' : stats.error ? '?' : stats.requests}
              </div>
              <div className="stat-label">Pending Requests</div>
            </div>
          </div>
          {stats.error && (
            <div className="stat-error" role="alert">{stats.error}</div>
          )}
        </div>
      </section>

      {/* How It Works */}
      <section className="landing-how-it-works">
        <div className="landing-section-content">
          <h3>How It Works</h3>
          <div className="steps-grid">
            <div className="step">
              <div className="step-number">1</div>
              <h4>Add Items</h4>
              <p>Create a catalog of items you want to lend or borrow.</p>
            </div>
            <div className="step">
              <div className="step-number">2</div>
              <h4>Request & Approve</h4>
              <p>Send borrow requests and approve them from incoming ones.</p>
            </div>
            <div className="step">
              <div className="step-number">3</div>
              <h4>Track & Return</h4>
              <p>Monitor active loans and manage returns with ease.</p>
            </div>
          </div>
        </div>
      </section>

      {/* Final CTA */}
      <section className="landing-footer-cta">
        <div className="landing-section-content">
          <h3>Ready to get started?</h3>
          <button className="landing-primary-btn" onClick={handleEnter}>
            Enter Workspace
          </button>
        </div>
      </section>
    </div>
  )
}
