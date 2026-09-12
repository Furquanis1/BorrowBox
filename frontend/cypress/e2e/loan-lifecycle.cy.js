/**
 * V2.2.3 Loan Lifecycle Coverage
 *
 * Drives an approved Football reservation through the whole physical loan
 * lifecycle against the running backend + seeded MySQL DB:
 *
 *   PENDING → APPROVED → AWAITING_HANDOVER → ACTIVE → RETURN_INITIATED
 *     → RETURN_REPORTED → COMPLETED
 *
 * Verifies the authoritative timestamps, the shared availability counts at
 * each step, and the Loans UI return actions for both borrower and lender.
 *
 * All transactions created here carry a unique purpose marker; the `after`
 * hook closes or releases every one of them so the canonical seeded fixture
 * (Football 2 units: 1 AVAILABLE + 1 RESERVED, 0 borrowed) is restored for the
 * rest of the test suite regardless of where the spec stopped.
 */
describe('V2.2.3 Loan Lifecycle', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }

  let marker
  let cseId
  let listingId

  // Requests whose URL interpolates a runtime value must defer interpolation
  // to the execution step; passing a URL builder (thunk) keeps the value fresh.
  const post = (buildPath, body) =>
    cy.wrap(null).then(() => cy.request({ method: 'POST', url: buildPath(), body }))
  const get = (buildPath) => cy.wrap(null).then(() => cy.request('GET', buildPath()))

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

  const loginViaApi = (user) => {
    cy.request({ method: 'POST', url: '/api/auth/login', body: user, failOnStatusCode: true })
  }

  const footballCounts = () =>
    cy.wrap(null).then(() =>
      cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
        const football = res.body.find((l) => l.title === 'Football')
        return { available: football.availableUnits, borrowed: football.borrowedUnits }
      }),
    )

  const closeMarkerTxn = (txn) => {
    switch (txn.state) {
      case 'PENDING':
        loginViaApi(salah)
        cy.request('POST', `/api/transactions/${txn.id}/cancel`)
        break
      case 'AWAITING_HANDOVER':
        loginViaApi(ahmed)
        cy.request('POST', `/api/transactions/${txn.id}/cancel`)
        break
      case 'APPROVED':
        loginViaApi(ahmed)
        cy.request('POST', `/api/transactions/${txn.id}/stage-handover`)
        cy.request('POST', `/api/transactions/${txn.id}/cancel`)
        break
      case 'ACTIVE':
        loginViaApi(salah)
        cy.request('POST', `/api/transactions/${txn.id}/initiate-return`)
        cy.request('POST', `/api/transactions/${txn.id}/report-handback`)
        loginViaApi(ahmed)
        cy.request('POST', `/api/transactions/${txn.id}/confirm-return`)
        break
      case 'RETURN_INITIATED':
        loginViaApi(salah)
        cy.request('POST', `/api/transactions/${txn.id}/report-handback`)
        loginViaApi(ahmed)
        cy.request('POST', `/api/transactions/${txn.id}/confirm-return`)
        break
      default:
        break
    }
  }

  before(() => {
    marker = `Lifecycle-${Date.now()}`
    loginViaApi(ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
    })
    cy.then(() => {
      return cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
        listingId = res.body.find((l) => l.title === 'Football').id
      })
    })
  })

  after(() => {
    loginViaApi(ahmed)
    cy.request('GET', '/api/me/lend-requests').then((res) => {
      res.body
        .filter((t) => t.purpose.startsWith(marker))
        .forEach((txn) => closeMarkerTxn(txn))
    })
    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })
  })

  it('runs a full loan through handover, return, and receipt', () => {
    // ── Create + approve ────────────────────────────────────────────
    let txnId
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} Weekend match`, requestedDurationDays: 2 },
    }).then((res) => {
      txnId = res.body.id
      expect(res.body.state).to.equal('PENDING')
      expect(res.body.reservationHeld).to.equal(true)
      expect(res.body.startedAt).to.equal(null)
      expect(res.body.completedAt).to.equal(null)
    })

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`).then((res) => {
      expect(res.body.state).to.equal('APPROVED')
    })

    // ── Stage + confirm handover → ACTIVE ───────────────────────────
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`).then((res) => {
      expect(res.body.state).to.equal('AWAITING_HANDOVER')
      expect(res.body.reservationHeld).to.equal(true)
    })

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/confirm-handover`).then((res) => {
      expect(res.body.state).to.equal('ACTIVE')
      expect(res.body.startedAt).to.not.equal(null)
    })

    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(0)
      expect(borrowed).to.equal(1)
    })

    // ── Borrower UI: active loan; return is initiated from the conversation ──
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.url({ timeout: 15000 }).should('include', '/me/loans')
    cy.get('.active-loans').should('be.visible')
    cy.get('.transaction-list').contains('Football').should('be.visible')
    cy.get('.transaction-card').contains('On loan').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-body').should('be.visible')
    cy.get('.conversation-return-action').contains('Ready to return the item?').should('be.visible')
    cy.get('.conversation-return-action button').contains('Start return').click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Return initiated').should('be.visible')

    // RETURN_INITIATED: the borrower reports the physical handback.
    cy.get('.conversation-return-action button').contains("I've handed the item back").click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Handback reported').should('be.visible')
    cy.get('.conversation-return-action').should('not.exist')
    cy.get('.conversation-return-status').contains('Handback reported. Awaiting the owner').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    // RETURN_REPORTED shows on Loans; the Requests inbox no longer lists it.
    cy.get('.transaction-card', { timeout: 15000 }).contains('Return reported').should('be.visible')
    cy.visit('/me/requests')
    cy.get('.requests-tab').contains('My requests').click()
    cy.get('.transaction-card').should('not.contain', marker)

    // ── Lender UI: confirm receipt from the conversation → COMPLETED ──
    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.url({ timeout: 15000 }).should('include', '/me/loans')
    cy.get('.transaction-card').contains('Return reported').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-return-action').contains('The borrower reported the item is back.').should('be.visible')
    cy.get('.conversation-return-action button').contains('Confirm received').click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Loan completed').should('be.visible')
    cy.get('button[aria-label="Close"]').click()
    cy.get('.transaction-card', { timeout: 15000 }).contains('Completed').should('be.visible')

    // ── Final API assertions ────────────────────────────────────────
    get(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.body.state).to.equal('COMPLETED')
      expect(res.body.startedAt).to.not.equal(null)
      expect(res.body.completedAt).to.not.equal(null)
      expect(res.body.reservationHeld).to.equal(false)
    })

    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })
  })
})