import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import Button from '../components/ui/Button'

const FEATURES = [
  {
    icon: 'bi-people',
    title: 'Built for circles',
    body: 'Friends, classmates, colleagues, and neighbors share inside a community they already trust.',
  },
  {
    icon: 'bi-arrow-left-right',
    title: 'Borrow with care',
    body: 'Clear requests, agreed durations, handover notes, and return evidence keep every loan gentle and accountable.',
  },
  {
    icon: 'bi-shield-check',
    title: 'Trust you can see',
    body: 'A community-scoped ledger records on-time returns and careful lending without turning sharing into a scoreboard.',
  },
]

const STEPS = [
  {
    number: '1',
    title: 'Add Items',
    body: 'Add something you own and offer it to the circles you belong to.',
  },
  {
    number: '2',
    title: 'Request & Approve',
    body: 'Agree on purpose, duration, and pickup with the person lending it.',
  },
  {
    number: '3',
    title: 'Track & Return',
    body: 'Follow the loan, care for the item, and close the loop with return evidence.',
  },
]

const PREVIEW_TABS = [
  { id: 'timeline', label: 'Borrowing timeline', icon: 'bi-clock-history' },
  { id: 'ledger', label: 'Mutual trust ledger', icon: 'bi-stars' },
  { id: 'care', label: 'Four-moment care', icon: 'bi-heart' },
]

const PREVIEW_CONTENT = {
  timeline: [
    { time: 'Monday', title: 'Salah requested the football', note: 'Practice after class · 3 days' },
    { time: 'Tuesday', title: 'Ahmed approved the request', note: 'Purpose and duration agreed' },
    { time: 'Thursday', title: 'Handover confirmed', note: 'Condition noted by both people' },
  ],
  ledger: [
    { time: '12', title: 'On-time returns', note: 'Salah · CSE Department' },
    { time: '8', title: 'Careful returns', note: 'Karim · Engineering Office' },
    { time: '5', title: 'Trusted lends', note: 'Ahmed · Hostel Block B' },
  ],
  care: [
    { time: '01', title: 'Before', note: 'Agree on purpose, duration, and starting condition' },
    { time: '02', title: 'At handover', note: 'Confirm the condition together' },
    { time: '03', title: 'In use', note: 'Care for the item as if it were your own' },
    { time: '04', title: 'On return', note: 'Capture the return and close the loop' },
  ],
}

const CIRCLE_PEOPLE = [
  { icon: 'bi-person', label: 'Friend' },
  { icon: 'bi-mortarboard', label: 'Classmate' },
  { icon: 'bi-briefcase', label: 'Colleague' },
  { icon: 'bi-house', label: 'Neighbor' },
]

export default function LandingPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [preview, setPreview] = useState('timeline')

  const handleEnter = () => {
    if (user) {
      navigate('/dashboard')
    } else {
      navigate('/signin')
    }
  }

  const handleGetStarted = () => {
    if (user) {
      navigate('/dashboard')
    } else {
      navigate('/signup')
    }
  }

  const handleSignIn = () => navigate('/signin')

  return (
    <div className="landing-page">
      {/* Editorial hero */}
      <section className="landing-hero" aria-labelledby="hero-heading">
        <div className="landing-hero-content">
          <div className="landing-hero-copy">
            <span className="landing-eyebrow">A QUIETER WAY TO SHARE</span>
            <h1 id="hero-heading">Manage Borrowing &amp; Lending Simply</h1>
            <p className="landing-subheading">
              BorrowBox is a calm home for peer-to-peer sharing inside close circles of 5 to 500
              people — friends, classmates, colleagues, and neighbors who look after each other&apos;s
              things.
            </p>
            <div className="landing-hero-actions">
              <Button
                size="lg"
                className="landing-primary-btn"
                onClick={handleGetStarted}
              >
                Get started
                <i className="bi bi-arrow-right" aria-hidden="true" />
              </Button>
              <Button size="lg" variant="outline" className="landing-secondary-btn" onClick={handleSignIn}>
                Sign in
              </Button>
            </div>
            <p className="landing-hero-footnote">
              Built around sharing, caring, and mutual trust — never a marketplace.
            </p>
          </div>

          <div className="landing-preview" aria-label="Community sharing preview">
            <div className="landing-preview-head">
              <span className="landing-preview-dots" aria-hidden="true">
                <i />
                <i />
                <i />
              </span>
              <span className="landing-preview-title">CSE Department · Community sharing</span>
            </div>
            <div className="landing-preview-body">
              <div className="landing-preview-summary">
                <span className="landing-preview-avatars" aria-hidden="true">
                  <i className="bi bi-person" />
                  <i className="bi bi-person" />
                  <i className="bi bi-person" />
                </span>
                <span>
                  <strong>24 neighbors in one circle</strong>
                  <small>Shared inventory, visible to the people who trust it</small>
                </span>
              </div>

              <div className="landing-preview-tabs" role="group" aria-label="Preview views">
                {PREVIEW_TABS.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    className={`landing-preview-tab${preview === tab.id ? ' is-active' : ''}`}
                    aria-pressed={preview === tab.id}
                    onClick={() => setPreview(tab.id)}
                  >
                    <i className={`bi ${tab.icon}`} aria-hidden="true" />
                    {tab.label}
                  </button>
                ))}
              </div>

              <ul className="landing-preview-list">
                {PREVIEW_CONTENT[preview].map((row) => (
                  <li key={`${preview}-${row.title}`} className="landing-preview-row">
                    <span className="landing-preview-marker">{row.time}</span>
                    <span className="landing-preview-row-copy">
                      <strong>{row.title}</strong>
                      <small>{row.note}</small>
                    </span>
                    <i className="bi bi-check2" aria-hidden="true" />
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* Trust circle showcase */}
      <section className="landing-circle" aria-labelledby="circle-heading">
        <div className="landing-circle-content">
          <div className="landing-circle-copy">
            <span className="landing-eyebrow">TRUST CIRCLES</span>
            <h2 id="circle-heading">Made for the people around you</h2>
            <p className="landing-section-sub">
              A circle can be five friends or five hundred classmates. BorrowBox keeps sharing
              personal, bounded, and familiar — with no marketplace in the middle.
            </p>
            <div className="landing-circle-values">
              <span><i className="bi bi-share" aria-hidden="true" /> Sharing</span>
              <span><i className="bi bi-heart" aria-hidden="true" /> Caring</span>
              <span><i className="bi bi-shield-check" aria-hidden="true" /> Mutual trust</span>
            </div>
          </div>
          <div className="landing-circle-visual" aria-label="Friends, classmates, colleagues, and neighbors in one trust circle">
            <div className="landing-circle-ring" aria-hidden="true" />
            {CIRCLE_PEOPLE.map((person, index) => (
              <span
                key={person.label}
                className={`landing-circle-person landing-circle-person-${index + 1}`}
                title={person.label}
              >
                <i className={`bi ${person.icon}`} aria-hidden="true" />
              </span>
            ))}
            <div className="landing-circle-core">
              <strong>5–500</strong>
              <span>people</span>
            </div>
          </div>
        </div>
      </section>

      {/* What's in BorrowBox */}
      <section className="landing-features" aria-labelledby="features-heading">
        <div className="landing-features-content">
          <h2 id="features-heading">What&apos;s in BorrowBox</h2>
          <p className="landing-section-sub">
            Everything a close circle needs to lend and borrow with confidence.
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
          <p className="landing-section-sub">Three simple steps keep sharing friendly and calm.</p>
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
        <div className="landing-cta-card">
          <span className="landing-eyebrow">YOUR CIRCLE, YOUR RHYTHM</span>
          <h2 id="cta-heading">Ready to get started?</h2>
          <p className="landing-section-sub">Bring your circle together and open a calmer shared space.</p>
          <Button size="lg" onClick={handleEnter}>
            Start Now
            <i className="bi bi-arrow-right" aria-hidden="true" />
          </Button>
        </div>
      </section>
    </div>
  )
}
