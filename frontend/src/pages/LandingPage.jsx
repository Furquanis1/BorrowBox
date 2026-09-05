import React from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import Button from '../components/ui/Button'

const FEATURES = [
  {
    icon: 'bi-people',
    title: 'Built for communities',
    body: 'Members, groups, and shared spaces keep everything organized around the people you trust.',
  },
  {
    icon: 'bi-box-seam',
    title: 'Borrow with confidence',
    body: 'Clear requests, approvals, and due dates turn borrowing into a simple, trustworthy habit.',
  },
  {
    icon: 'bi-clipboard-check',
    title: 'Everything in one place',
    body: 'Items, pending requests, and active loans tracked from one clean workspace.',
  },
]

const STEPS = [
  {
    number: '1',
    title: 'Add Items',
    body: 'Create a catalog of items you want to lend or borrow.',
  },
  {
    number: '2',
    title: 'Request & Approve',
    body: 'Send borrow requests and approve them from incoming ones.',
  },
  {
    number: '3',
    title: 'Track & Return',
    body: 'Monitor active loans and manage returns with ease.',
  },
]

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

  return (
    <div className="landing-page">
      {/* Hero Section */}
      <section className="landing-hero" aria-labelledby="hero-heading">
        <div className="landing-hero-content">
          <h1 id="hero-heading">Manage Borrowing &amp; Lending Simply</h1>
          <p className="landing-subheading">
            Track items, manage borrow requests, and keep everyone on the same page — all in one
            warm, calm community workspace.
          </p>
          <Button size="lg" className="landing-primary-btn" onClick={handleEnter}>
            Start Now
            <i className="bi bi-arrow-right" aria-hidden="true" />
          </Button>
        </div>
      </section>

      {/* What's in BorrowBox */}
      <section className="landing-features" aria-labelledby="features-heading">
        <div className="landing-features-content">
          <h2 id="features-heading">What's in BorrowBox</h2>
          <p className="landing-section-sub">
            A shared space for communities to lend, borrow, and keep track of everything.
          </p>
          <div className="feature-grid">
            {FEATURES.map((feature) => (
              <div className="feature-card" key={feature.title}>
                <span className="feature-card-icon" aria-hidden="true">
                  <i className={`bi ${feature.icon}`} />
                </span>
                <h3>{feature.title}</h3>
                <p>{feature.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section className="landing-how-it-works" id="how-it-works" aria-labelledby="how-heading">
        <div className="landing-section-content">
          <h2 id="how-heading">How It Works</h2>
          <p className="landing-section-sub">Three simple steps keep lending friendly and friction-free.</p>
          <div className="steps-grid">
            {STEPS.map((step) => (
              <div className="step" key={step.number}>
                <div className="step-number" aria-hidden="true">
                  {step.number}
                </div>
                <h3>{step.title}</h3>
                <p>{step.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Final CTA */}
      <section className="landing-cta" aria-labelledby="cta-heading">
        <h2 id="cta-heading">Ready to get started?</h2>
        <p className="landing-section-sub">Join a community and open your workspace.</p>
        <Button size="lg" onClick={handleEnter}>
          Enter Workspace
          <i className="bi bi-arrow-right" aria-hidden="true" />
        </Button>
      </section>
    </div>
  )
}