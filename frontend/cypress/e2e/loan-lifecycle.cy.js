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
    // V2.2.8: mark this user's event deliveries READ so the global Event
    // Envelope does not overlay the legacy loan/return UI on the next load.
    cy.request({ method: 'POST', url: '/api/me/events/read-all' })
  }

  const loginViaApi = (user) => {
    cy.request({ method: 'POST', url: '/api/auth/login', body: user, failOnStatusCode: true })
  }

  // V2.2.6: system-issued evidence uploads. The browser builds a multipart body;
  // the binary content is arbitrary (only content-type + size are validated).
  // `buildTxnId` may be a value or a thunk; the id is read at execution time.
  const uploadPhoto = (buildTxnId, type) =>
    cy.wrap(null).then(() => {
      const id = typeof buildTxnId === 'function' ? buildTxnId() : buildTxnId
      const form = new FormData()
      form.append('type', type)
      form.append('file', new Blob([new Uint8Array([137, 80, 78, 71, 1, 2, 3, 4])], { type: 'image/png' }), 'photo.png')
      return cy.request({ method: 'POST', url: `/api/transactions/${id}/evidence`, body: form, failOnStatusCode: true })
    })

  const uploadReturnEvidence = (txnId) => {
    uploadPhoto(txnId, 'BORROWER_PRE_RETURN')
    uploadPhoto(txnId, 'BORROWER_RETURN_HANDOVER')
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
        uploadReturnEvidence(txn.id)
        cy.request('POST', `/api/transactions/${txn.id}/report-handback`)
        loginViaApi(ahmed)
        cy.request('POST', `/api/transactions/${txn.id}/confirm-return`)
        break
      case 'RETURN_INITIATED':
        loginViaApi(salah)
        uploadReturnEvidence(txn.id)
        cy.request('POST', `/api/transactions/${txn.id}/report-handback`)
        loginViaApi(ahmed)
        cy.request('POST', `/api/transactions/${txn.id}/confirm-return`)
        break
      case 'RETURN_REPORTED':
        loginViaApi(ahmed)
        cy.request('POST', `/api/transactions/${txn.id}/confirm-return`)
        break
      case 'HANDOVER_DISPUTED':
        // V2.2.4: the unit was already released back to AVAILABLE; nothing to
        // clean up, only ignore so the marker never drifts inventory.
        break
      case 'RETURN_DISPUTED':
        // V2.2.6: the unit is frozen BORROWED; no product API can reverse it.
        // Restore inventory directly in MySQL.
        cy.task('restoreReturnDispute', txn.id)
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

  afterEach(() => {
    // Run after every test (even failed ones) so a marker loan left ACTIVE or
    // in a handover state is driven back to terminal, never blocking the next
    // test from reserving the single AVAILABLE Football unit.
    loginViaApi(ahmed)
    cy.request('GET', '/api/me/lend-requests').then((res) => {
      res.body
        .filter((t) => t.purpose.startsWith(marker))
        .forEach((txn) => closeMarkerTxn(txn))
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
      expect(res.body.dueAt).to.not.equal(null)
      expect(res.body.originalDueAt).to.equal(res.body.dueAt)
      expect(res.body.dueSoon).to.equal(false)
      expect(res.body.overdue).to.equal(false)
      expect(res.body.handoverWindowOpen).to.equal(true)
      expect(res.body.borrowerConfirmedAt).to.equal(null)
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

    // RETURN_INITIATED: both photos are required; the UI gates the handback report.
    cy.get('.conversation-return-action button').contains("I've handed the item back").should('be.disabled')
    cy.get('.conversation-return-action input[type="file"]').eq(0).selectFile('cypress/fixtures/photo.png', { force: true })
    cy.get('.conversation-return-action button').contains('Before-return photo added', { timeout: 15000 }).scrollIntoView().should('be.visible')
    cy.get('.conversation-return-action input[type="file"]').eq(1).selectFile('cypress/fixtures/photo.png', { force: true })
    cy.get('.conversation-return-action button').contains('Handover photo added', { timeout: 15000 }).scrollIntoView().should('be.visible')
    cy.get('.conversation-return-action button').contains("I've handed the item back").should('be.enabled').scrollIntoView().click()
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

  it('borrower confirms receipt within the handover window', () => {
    let txnId
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} Confirm receipt`, requestedDurationDays: 2 },
    }).then((res) => {
      txnId = res.body.id
    })

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/confirm-handover`).then((res) => {
      expect(res.body.handoverWindowOpen).to.equal(true)
    })

    // V2.2.4: borrower explicitly confirms receipt while the window is open.
    loginViaApi(salah)
    cy.wrap(null)
      .then(() => cy.request({ method: 'POST', url: `/api/transactions/${txnId}/confirm-receipt` }))
      .then((res) => {
        expect(res.body.state).to.equal('ACTIVE')
        expect(res.body.borrowerConfirmedAt).to.not.equal(null)
        expect(res.body.handoverWindowOpen).to.equal(true)
      })

    // The confirmation is a SYSTEM conversation event, never a user-editable row.
    get(() => `/api/transactions/${txnId}/messages`).then((res) => {
      const bodies = res.body.map((m) => m.body)
      expect(bodies).to.include('Borrower confirmed receipt')
      const event = res.body.find((m) => m.body === 'Borrower confirmed receipt')
      expect(event.kind).to.equal('SYSTEM')
      expect(event.authorId).to.equal(null)
    })

    // A second confirmation must be rejected.
    cy.wrap(null)
      .then(() =>
        cy.request({
          method: 'POST',
          url: `/api/transactions/${txnId}/confirm-receipt`,
          failOnStatusCode: false,
        }),
      )
      .then((res) => {
        expect(res.status).to.equal(400)
      })

    // Close the marker so the canonical fixture is restored for later specs.
    post(() => `/api/transactions/${txnId}/initiate-return`)
    uploadReturnEvidence(() => txnId)
    post(() => `/api/transactions/${txnId}/report-handback`)
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/confirm-return`)

    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })
  })

  it('borrower disputing the handover releases the unit and resolves transaction read-only', () => {
    let txnId
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} Dispute handover`, requestedDurationDays: 2 },
    }).then((res) => {
      txnId = res.body.id
    })

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/confirm-handover`)

    // The unit is now BORROWED; no other Football unit is available.
    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(0)
      expect(borrowed).to.equal(1)
    })

    // V2.2.4: borrower disputes non-receipt within the window.
    loginViaApi(salah)
    cy.wrap(null)
      .then(() => cy.request({ method: 'POST', url: `/api/transactions/${txnId}/dispute-handover` }))
      .then((res) => {
        expect(res.body.state).to.equal('HANDOVER_DISPUTED')
        expect(res.body.reservationHeld).to.equal(false)
      })

    // The borrowed unit is released back to AVAILABLE immediately.
    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })

    // The dispute is recorded as a SYSTEM event.
    get(() => `/api/transactions/${txnId}/messages`).then((res) => {
      const event = res.body.find((m) => m.body === 'Handover disputed')
      expect(event).to.exist
      expect(event.kind).to.equal('SYSTEM')
    })

    // HANDOVER_DISPUTED has no forward transitions.
    cy.wrap(null)
      .then(() => cy.request({ method: 'POST', url: `/api/transactions/${txnId}/initiate-return`, failOnStatusCode: false }))
      .then((res) => {
        expect(res.status).to.equal(400)
      })

    // The conversation is read-only: the Loans UI shows the terminal state.
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains('Handover disputed').should('be.visible')
    cy.get('.transaction-card').contains('button', 'View conversation').click()
    cy.get('.conversation-body').should('be.visible')
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Handover disputed').should('be.visible')
    cy.get('.conversation-return-status').contains('Handover disputed. The item has been returned to available inventory.')
      .should('be.visible')
    cy.get('.conversation-composer').should('not.exist')
  })
})
