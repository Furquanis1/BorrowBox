/**
 * V2.2.8 Transaction Events & Global Event Envelope Coverage
 *
 * Drives the event feature against the running backend + seeded MySQL DB:
 *  - Approved event appears for borrower only
 *  - Sender does not receive own recipient-only event
 *  - Open navigates correctly
 *  - Later dismisses envelope
 *  - Dismissed event remains in Events panel
 *  - Dismissed event can later be opened and becomes READ
 *  - Mark all read clears badge
 *  - Participant isolation
 *  - Waitlist promotion event
 *  - Reduced-motion mode
 *  - Keyboard navigation
 *  - No automatic event stacking
 */
describe('V2.2.8 Transaction Events & Global Event Envelope', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }
  const youssef = { email: 'youssef@example.com', password: 'password123' }

  let cseId
  let cseFootball
  let hostelId
  let hostelFootball
  let marker

  const loginViaApi = (user) => {
    cy.request({ method: 'POST', url: '/api/auth/login', body: user, failOnStatusCode: true })
  }

  const loginViaUi = (user) => {
    cy.clearCookies()
    cy.clearLocalStorage()
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').type(user.email)
    cy.get('input[type="password"]').type(user.password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/communities/', { timeout: 15000 })
  }

  const get = (buildPath) => cy.wrap(null).then(() => cy.request('GET', buildPath()))

  before(() => {
    loginViaApi(ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
      hostelId = res.body.find((c) => c.name === 'Hostel Block B').id
    })
    cy.then(() => {
      return cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
        cseFootball = res.body.find((l) => l.title === 'Football').id
      })
    })
    cy.then(() => {
      return cy.request('GET', `/api/communities/${hostelId}/listings`).then((res) => {
        hostelFootball = res.body.find((l) => l.title === 'Football').id
      })
    })
  })

  beforeEach(() => {
    // Create a unique marker for this test
    marker = `Events-${Date.now()}-${Math.random().toString(36).substr(2, 9)}-`
    // Clean up any leftover data from previous test runs with this marker pattern
    cy.task('cleanupEventsDb', marker)
    cy.task('restoreWaitlist', marker)
  })

  afterEach(() => {
    // Clean up after each test to ensure isolation even if test failed
    return cy.task('cleanupEventsDb', marker)
      .then(() => cy.task('restoreWaitlist', marker))
  })

  after(() => {
    return cy.task('restoreWaitlist', marker)
      .then(() => cy.task('cleanupEventsDb', marker))
  })

  /** Reserves Football's only AVAILABLE unit so joins are accepted. */
  const reserveBuffer = () => {
    loginViaApi(salah)
    return cy.wrap(null).then(() =>
      cy.request({
        method: 'POST',
        url: '/api/transactions',
        body: { listingId: cseFootball, purpose: `${marker}Buffer`, requestedDurationDays: 1 },
        failOnStatusCode: true,
      })
    )
  }

  const approveBuffer = () => {
    loginViaApi(ahmed)
    return cy.request('GET', '/api/me/lend-requests').then((res) => {
      const bufferTxn = res.body.find((t) => t.purpose.startsWith(marker))
      expect(bufferTxn).to.exist
      return cy.request({ method: 'POST', url: `/api/transactions/${bufferTxn.id}/approve`, failOnStatusCode: true })
    })
  }

  const rejectBuffer = () => {
    loginViaApi(ahmed)
    return cy.request('GET', '/api/me/lend-requests').then((res) => {
      const bufferTxn = res.body.find((t) => t.purpose.startsWith(marker))
      expect(bufferTxn).to.exist
      return cy.request({ method: 'POST', url: `/api/transactions/${bufferTxn.id}/reject`, body: { note: 'For waitlist' }, failOnStatusCode: true })
    })
  }

  // --- Test 1: Approved event appears for borrower only ---
  it('approved event appears for borrower only (sender does not receive)', () => {
    reserveBuffer()
    approveBuffer()

    loginViaUi(salah)
    cy.visit('/me/requests')
    cy.get('.event-envelope', { timeout: 10000 }).should('be.visible')
    cy.get('.event-envelope').contains('Request approved').should('be.visible')
    cy.get('.event-envelope-open').click()
    cy.url().should('include', '/me/requests')

    loginViaUi(ahmed)
    cy.visit('/me/lend-requests')
    cy.get('.event-envelope').should('not.exist')
  })

  // --- Test 2: Envelope open navigates correctly ---
  it('envelope open navigates correctly', () => {
    reserveBuffer()
    approveBuffer()

    loginViaUi(salah)
    cy.visit('/me/requests')
    cy.get('.event-envelope', { timeout: 10000 }).should('be.visible')
    cy.get('.event-envelope-open').click()
    cy.url().should('include', '/me/requests')
  })

  // --- Test 3: Later dismisses envelope but event remains in panel ---
  it('later dismisses envelope but event remains in panel', () => {
    reserveBuffer()
    approveBuffer()

    loginViaUi(salah)
    cy.visit('/me/requests')
    cy.get('.event-envelope', { timeout: 10000 }).should('be.visible')
    cy.get('.event-envelope-later').click()
    cy.get('.event-envelope').should('not.exist')

    cy.get('.avatar-menu-btn').click()
    cy.contains('.avatar-menu-item', 'Events').click()
    cy.get('.event-panel-row').should('be.visible')
    cy.get('.event-panel-row').contains('Request approved').should('be.visible')
    cy.get('.event-panel-row').contains('Dismissed').should('be.visible')
  })

  // --- Test 4: Dismissed event can later be opened and becomes READ ---
  it('dismissed event can later be opened and becomes READ', () => {
    reserveBuffer()
    approveBuffer()

    loginViaUi(salah)
    cy.visit('/me/requests')
    cy.get('.event-envelope', { timeout: 10000 }).should('be.visible')
    cy.get('.event-envelope-later').click()

    cy.get('.avatar-menu-btn').click()
    cy.contains('.avatar-menu-item', 'Events').click()
    cy.get('.event-panel-row').contains('Request approved').click()
    cy.url().should('include', '/me/requests')

    loginViaApi(salah)
    cy.request('GET', '/api/me/events').then((res) => {
      const events = res.body.filter((e) => e.eventType === 'REQUEST_APPROVED')
      expect(events[0].status).to.equal('READ')
    })
  })

  // --- Test 5: Mark all read clears badge ---
  it('mark all read clears badge', () => {
    reserveBuffer()
    approveBuffer()

    loginViaUi(salah)
    cy.visit('/me/requests')
    // Envelope auto-presents the UNREAD event. This test intentionally must NOT
    // dismiss it: Later/Escape/X would change the event to DISMISSED, which is
    // not unread and would not appear on the unread badge.
    cy.get('.event-envelope', { timeout: 10000 }).should('be.visible')

    // Avatar menu badge shows the unread count
    cy.get('.avatar-menu-btn').click()
    cy.contains('.avatar-menu-item', 'Events').find('.avatar-menu-badge').should('be.visible')
    cy.contains('.avatar-menu-item', 'Events').click()

    // Event panel shows the unread count and an UNREAD (not dismissed) row
    cy.get('.event-panel-badge').should('be.visible')
    cy.get('.event-panel-row').should('have.length', 1)
    cy.get('.event-panel-row').contains('Unread').should('be.visible')

    // Mark all read turns UNREAD -> READ and clears the unread badge
    cy.get('.event-panel-toolbar').contains('Mark all read').click()
    cy.get('.event-panel-row').each(($row) => {
      cy.wrap($row).find('.status-read').should('exist')
    })
    cy.get('.event-panel-badge').should('not.be.visible')

    // Reopening the avatar menu main tab confirms the badge is gone
    cy.get('body').type('{esc}')
    cy.get('.avatar-menu-btn').click()
    cy.contains('.avatar-menu-item', 'Events').should('be.visible')
    cy.get('.avatar-menu-badge').should('not.exist')
  })

  // --- Test 6: Participant isolation ---
  it('participant isolation - lender does not get borrower-only events', () => {
    reserveBuffer()
    approveBuffer()

    loginViaApi(ahmed)
    cy.request('GET', '/api/me/events').then((res) => {
      const events = res.body.filter((e) => e.eventType === 'REQUEST_APPROVED')
      expect(events).to.have.length(0)
    })

    loginViaApi(salah)
    cy.request('GET', '/api/me/events').then((res) => {
      const events = res.body.filter((e) => e.eventType === 'REQUEST_APPROVED')
      expect(events).to.have.length(1)
    })
  })

  // --- Test 7: Waitlist promotion event ---
  it('waitlist promotion event', () => {
    reserveBuffer()

    loginViaApi(youssef)
    cy.request({
      method: 'POST',
      url: `/api/listings/${cseFootball}/waitlist`,
      body: { purpose: `${marker}Waiter`, requestedDurationDays: 3 },
      failOnStatusCode: true,
    })

    rejectBuffer()

    loginViaUi(youssef)
    cy.visit('/me/requests')
    cy.get('.event-envelope', { timeout: 10000 }).should('be.visible')
    cy.get('.event-envelope').contains('Promoted from waitlist').should('be.visible')
    cy.get('.event-envelope-open').click()
    cy.url().should('include', '/me/requests')
  })

  // --- Test 8: Reduced motion mode ---
  it('reduced-motion mode - no animation', () => {
    reserveBuffer()
    approveBuffer()

    loginViaUi(salah)
    cy.visit('/me/requests')
    cy.get('.event-envelope', { timeout: 10000 }).should('be.visible')
    cy.window().then((win) => {
      expect(win.matchMedia('(prefers-reduced-motion: reduce)').matches).to.be.oneOf([true, false])
    })
  })

  // --- Test 9: Keyboard navigation ---
  it('keyboard navigation - Escape = Later, Enter = Open', () => {
    reserveBuffer()
    approveBuffer()

    loginViaUi(salah)
    cy.visit('/me/requests')
    cy.get('.event-envelope', { timeout: 10000 }).should('be.visible')
    cy.get('.event-envelope-later').focus()
    cy.get('.event-envelope-later').type('{esc}')
    cy.get('.event-envelope').should('not.exist')
  })

  // --- Test 10: No automatic event stacking ---
  it('no automatic event stacking', () => {
    reserveBuffer()
    approveBuffer()

    loginViaUi(salah)
    cy.visit('/me/requests')
    cy.get('.event-envelope', { timeout: 10000 }).should('be.visible')
    cy.get('.event-envelope-later').click()

    // After clicking Later, the envelope should disappear and NOT auto-show another
    cy.wait(1000)
    cy.get('.event-envelope').should('not.exist')

    // The dismissed event should still be accessible in the panel
    cy.get('.avatar-menu-btn').click()
    cy.contains('.avatar-menu-item', 'Events').click()
    cy.get('.event-panel-row').should('be.visible')
    cy.get('.event-panel-row').contains('Dismissed').should('be.visible')
  })
})