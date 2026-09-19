/**
 * V2.2.7 Waitlist / Queueing Coverage
 *
 * Drives the waitlist feature against the running backend + seeded MySQL DB:
 *  - Join button only when every unit is unavailable; owners never see it.
 *  - One shared queue per Asset across communities (CSE + Hostel listings).
 *  - Waiting tab: position, leave, rejoin.
 *  - Promotion: a release promotes the head waiter into a PENDING transaction
 *    that carries a SYSTEM "Promoted from waitlist" event.
 *  - Join guard: submitting while a unit is available is rejected.
 *
 * Canonical fixture (like WaitlistIntegrationTest):
 *  - Football (Ahmed): 2 units, 1 AVAILABLE + 1 RESERVED (Salah's APPROVED
 *    "Football match practice" in CSE).
 *  - To reach 0 AVAILABLE the spec reserves the last unit with a PENDING
 *    "buffer" request created by salah; rejecting it frees the unit and can
 *    promote a waiter.
 *  - youssef: CSE. omar: Hostel. ahmed owns Football.
 *
 * All requests created here carry a unique purpose marker; the `after` hook
 * deletes leftover waitlist rows (cy.task) and closes each marker transaction
 * so the canonical fixture survives for the rest of the suite.
 */
describe('V2.2.7 Waitlist / Queueing', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }
  const youssef = { email: 'youssef@example.com', password: 'password123' }
  const omar = { email: 'omar@example.com', password: 'password123' }

  let marker
  let cseId
  let hostelId
  let cseFootball
  let hostelFootball

  const get = (buildPath) => cy.wrap(null).then(() => cy.request('GET', buildPath()))

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

  const footballCounts = () =>
    cy.wrap(null).then(() =>
      cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
        const football = res.body.find((l) => l.title === 'Football')
        return { available: football.availableUnits, borrowed: football.borrowedUnits }
      }),
    )

  const waitlistCount = () => {
    loginViaApi(salah)
    return cy.wrap(null).then(() =>
      cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
        return res.body.find((l) => l.title === 'Football').waitingCount
      }),
    )
  }

  /** Reserves Football's only AVAILABLE unit (asserting it exists first). */
  const reserveBuffer = () => {
    loginViaApi(salah)
    return cy.wrap(null).then(() => footballCounts().then(({ available }) => {
      expect(available).to.equal(1)
      return cy.request({
        method: 'POST',
        url: '/api/transactions',
        body: { listingId: cseFootball, purpose: `${marker}Buffer`, requestedDurationDays: 1 },
        failOnStatusCode: true,
      })
    }))
  }

  /** Closes any marker transaction the spec left holding a unit. */
  const closeMarkerTxn = (txn) => {
    if (txn.state !== 'PENDING' && txn.state !== 'APPROVED' && txn.state !== 'AWAITING_HANDOVER') return
    // Buffer requests belong to salah; promoted (Waiter) requests to youssef,
    // who can cancel their own PENDING request. Cleanup scans by purpose prefix.
    const borrower = txn.purpose.startsWith(`${marker}Buffer`) ? salah : youssef
    if (txn.state === 'APPROVED' || txn.state === 'AWAITING_HANDOVER') {
      loginViaApi(ahmed)
      cy.request({ method: 'POST', url: `/api/transactions/${txn.id}/cancel`, failOnStatusCode: false })
    } else {
      loginViaApi(borrower)
      cy.request({ method: 'POST', url: `/api/transactions/${txn.id}/cancel`, failOnStatusCode: false })
    }
  }

  const cleanMarkerTransactions = () => {
    loginViaApi(ahmed)
    cy.request('GET', '/api/me/lend-requests').then((res) => {
      res.body
        .filter((t) => t.purpose.startsWith(marker) || t.purpose.startsWith('Events-'))
        .forEach((txn) => closeMarkerTxn(txn))
    })
  }

  before(() => {
    marker = `Waitlist-${Date.now()}-`
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

  afterEach(() => {
    // Remove waitlist rows first so any later release finds no live waiter to
    // promote, then close marker transactions to restore canonical inventory.
    cy.task('restoreWaitlist', marker)
    cy.task('restoreEvents', marker)
    cleanMarkerTransactions()
  })

  after(() => {
    cy.task('restoreWaitlist', marker)
    cy.task('restoreEvents', marker)
    cleanMarkerTransactions()
    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })
    waitlistCount().then((count) => {
      expect(count || 0).to.equal(0)
    })
  })

  it('offers Join waitlist at zero availability; owners see only the count', () => {
    reserveBuffer()
    footballCounts().then(({ available }) => expect(available).to.equal(0))

    // Borrower UI: the row shows "Join waitlist" instead of "Request".
    loginViaUi(youssef)
    cy.visit(`/communities/${cseId}/explore`)
    cy.get('.explore-listing-row').contains('Football').should('be.visible')
    cy.contains('.explore-listing-row', 'Football').find('.request-item-button').should('not.exist')
    cy.contains('.explore-listing-row', 'Football').find('.join-waitlist-button').should('be.visible')

    // Join through the drawer; the toast reports the live position.
    cy.contains('.explore-listing-row', 'Football').find('.join-waitlist-button').click()
    cy.get('.request-drawer').should('be.visible')
    cy.get('.request-drawer').contains('Join waitlist for "Football"').should('be.visible')
    cy.get('.request-drawer-hint').should('be.visible')
    cy.get('#request-purpose').type(`${marker}Waiter`)
    cy.get('#request-duration').clear().type('3')
    cy.get('.request-drawer-actions button[type="submit"]').click()
    cy.get('.toast-message', { timeout: 15000 }).contains('You are #1 in the waitlist').should('be.visible')

    // The Explore row now reports the live waiting count.
    cy.get('.explore-listing-row').contains('Football').closest('.explore-listing-row')
      .should('contain', '1 waiting')

    // Owner AHMED sees only the shared count, never a join button on his own asset.
    loginViaUi(ahmed)
    cy.visit(`/communities/${cseId}/explore`)
    cy.contains('.explore-listing-row', 'Football').should('contain', '1 waiting')
    cy.contains('.explore-listing-row', 'Football').find('.join-waitlist-button').should('not.exist')
    cy.contains('.explore-listing-row', 'Football').find('.request-item-button').should('not.exist')

    // /me/waitlist is the owner's own list — empty, as owners never queue.
    get(() => '/api/me/waitlist').then((res) => {
      expect(res.body).to.have.length(0)
    })
  })

  it('Waiting tab shows the position; leaving then rejoining is allowed', () => {
    reserveBuffer()

    loginViaApi(youssef)
    cy.request({
      method: 'POST',
      url: `/api/listings/${cseFootball}/waitlist`,
      body: { purpose: `${marker}Waiter`, requestedDurationDays: 3 },
      failOnStatusCode: true,
    }).then((res) => {
      expect(res.status).to.equal(201)
      expect(res.body.status).to.equal('WAITING')
      expect(res.body.position).to.equal(1)
    })

    // Waiting tab surfaces the position.
    loginViaUi(youssef)
    cy.visit('/me/requests')
    cy.get('.requests-tab').contains('Waiting').click()
    cy.get('.waitlist-card').should('be.visible')
    cy.get('.waitlist-card').contains('Football').should('be.visible')
    cy.get('.waitlist-card').contains('Position #1').should('be.visible')

    // Leaving empties the Waiting tab.
    cy.get('.waitlist-card').contains('Leave waitlist').click()
    cy.get('.toast-message', { timeout: 15000 }).contains('You left the waitlist.').should('be.visible')
    cy.get('.waitlist-card').should('not.exist')

    // Rejoining is allowed and yields a fresh entry.
    loginViaApi(youssef)
    cy.request({
      method: 'POST',
      url: `/api/listings/${cseFootball}/waitlist`,
      body: { purpose: `${marker}Waiter`, requestedDurationDays: 3 },
    }).then((res) => {
      expect(res.body.status).to.equal('WAITING')
      expect(res.body.position).to.equal(1)
    })

    // A duplicate live position on the same Asset is rejected.
    cy.request({
      method: 'POST',
      url: `/api/listings/${cseFootball}/waitlist`,
      body: { purpose: `${marker}Waiter`, requestedDurationDays: 3 },
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.equal(400)
    })
  })

  it('one shared queue per Asset across communities', () => {
    reserveBuffer()

    loginViaApi(youssef)
    cy.request({
      method: 'POST',
      url: `/api/listings/${cseFootball}/waitlist`,
      body: { purpose: `${marker}Waiter`, requestedDurationDays: 3 },
    }).then((res) => {
      expect(res.body.position).to.equal(1)
    })

    // omar queues through the Hostel listing for the same Football asset.
    loginViaApi(omar)
    cy.request({
      method: 'POST',
      url: `/api/listings/${hostelFootball}/waitlist`,
      body: { purpose: `${marker}Waiter`, requestedDurationDays: 2 },
    }).then((res) => {
      expect(res.body.position).to.equal(2)
    })

    // Both waiters share one queue; the CSE listing reports 2 waiting.
    get(() => '/api/me/waitlist').then((res) => {
      expect(res.body).to.have.length(1)
      expect(res.body[0].assetTitle).to.equal('Football')
      expect(res.body[0].position).to.equal(2)
    })
    waitlistCount().then((count) => expect(count).to.equal(2))
  })

  it('releasing a unit promotes the head waiter into a PENDING request', () => {
    reserveBuffer()

    loginViaApi(youssef)
    cy.request({
      method: 'POST',
      url: `/api/listings/${cseFootball}/waitlist`,
      body: { purpose: `${marker}Waiter`, requestedDurationDays: 3 },
    }).then((res) => {
      expect(res.body.position).to.equal(1)
    })

    // Rejecting the buffer frees the unit and promotes the only waiter.
    loginViaApi(ahmed)
    cy.wrap(null).then(() =>
      cy.request('GET', '/api/me/lend-requests').then((res) => {
        const buffer = res.body.find((t) => t.purpose === `${marker}Buffer`)
        expect(buffer).to.exist
        return cy.request({ method: 'POST', url: `/api/transactions/${buffer.id}/reject`, body: { note: 'Releasing for the waitlist' } })
      }),
    ).then((res) => {
      expect(res.body.state).to.equal('REJECTED')
    })

    // The promoted request exists as a normal PENDING transaction holding a unit.
    cy.wrap(null).then(() =>
      cy.request('GET', '/api/me/lend-requests').then((res) => {
        const promoted = res.body.find((t) => t.purpose === `${marker}Waiter`)
        expect(promoted).to.exist
        expect(promoted.state).to.equal('PENDING')
        expect(promoted.reservationHeld).to.equal(true)
        // The system event is recorded on the promoted conversation.
        return cy.request('GET', `/api/transactions/${promoted.id}/messages`)
      }),
    ).then((res) => {
      const event = res.body.find((m) => m.body === 'Promoted from waitlist')
      expect(event).to.exist
      expect(event.kind).to.equal('SYSTEM')
      expect(event.authorId).to.equal(null)
    })

    // The waiter is no longer listed on /me/waitlist.
    get(() => '/api/me/waitlist').then((res) => {
      expect(res.body).to.have.length(0)
    })

    // UI: the promoted request appears under My requests; Waiting is empty.
    loginViaUi(youssef)
    cy.visit('/me/requests')
    cy.get('.requests-tab').contains('My requests').click()
    cy.get('.transaction-card').contains(`${marker}Waiter`).should('be.visible')
    cy.get('.transaction-card').contains('Pending').should('be.visible')
    cy.get('.requests-tab').contains('Waiting').click()
    cy.get('.waitlist-card').should('not.exist')
  })

  it('joining while a unit is available is rejected', () => {
    // Canonical Football: 1 AVAILABLE — no waitlist entry is accepted.
    loginViaApi(youssef)
    cy.request({
      method: 'POST',
      url: `/api/listings/${cseFootball}/waitlist`,
      body: { purpose: `${marker}Waiter`, requestedDurationDays: 3 },
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.equal(400)
      expect(res.body.error).to.contain('submit a normal request instead')
    })

    // And the UI offers "Request", not "Join waitlist", while a unit is free.
    loginViaUi(youssef)
    cy.visit(`/communities/${cseId}/explore`)
    cy.contains('.explore-listing-row', 'Football').find('.join-waitlist-button').should('not.exist')
    cy.contains('.explore-listing-row', 'Football').find('.request-item-button').should('be.visible')
  })
})