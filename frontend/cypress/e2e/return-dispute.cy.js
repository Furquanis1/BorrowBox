/**
 * V2.2.6 Return Dispute Coverage
 *
 * Drives a Football loan through the return phase and the lender's
 * "not received" dispute:
 *
 *   ACTIVE → RETURN_INITIATED → RETURN_REPORTED → RETURN_DISPUTED
 *
 * Verifies the V2.2.6 contract for return-side evidence:
 *   - both return photos are required before report-handback succeeds,
 *   - evidence is visible to both participants in the conversation,
 *   - the lender's "Not received" action freezes the loan at RETURN_DISPUTED,
 *   - the disputed AssetUnit stays BORROWED (reservation retained),
 *   - the terminal conversation is read-only (no composer, writes rejected),
 *   - RETURN_DISPUTED has no forward transitions.
 *
 * RETURN_DISPUTED is terminal by design with the unit frozen BORROWED and no
 * product API that can reverse it, so the `after` hook restores the canonical
 * seeded fixture (Football 1 AVAILABLE + 1 RESERVED, 0 borrowed) directly in
 * MySQL through the registered `restoreReturnDispute` task. This spec only
 * creates one marker transaction; cleanup always restores inventory so later
 * specs never see a missing AVAILABLE Football unit.
 */
describe('V2.2.6 Return Dispute', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }

  let marker
  let cseId
  let listingId

  const post = (buildPath, body) =>
    cy.wrap(null).then(() => cy.request({ method: 'POST', url: buildPath(), body }))

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
    // Envelope does not overlay the legacy return-dispute UI on the next load.
    cy.request({ method: 'POST', url: '/api/me/events/read-all' })
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

  const uploadPhoto = (buildTxnId, type) =>
    cy.wrap(null).then(() => {
      const id = typeof buildTxnId === 'function' ? buildTxnId() : buildTxnId
      const form = new FormData()
      form.append('type', type)
      form.append('file', new Blob([new Uint8Array([137, 80, 78, 71, 1, 2, 3, 4])], { type: 'image/png' }), 'photo.png')
      return cy.request({ method: 'POST', url: `/api/transactions/${id}/evidence`, body: form, failOnStatusCode: true })
    })

  const uploadReturnEvidence = (id) => {
    uploadPhoto(id, 'BORROWER_PRE_RETURN')
    uploadPhoto(id, 'BORROWER_RETURN_HANDOVER')
  }

  const closeMarkerTxn = (txn) => {
    switch (txn.state) {
      case 'PENDING':
        loginViaApi(salah)
        cy.request('POST', `/api/transactions/${txn.id}/cancel`)
        break
      case 'AWAITING_HANDOVER':
      case 'APPROVED':
        loginViaApi(ahmed)
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
      case 'RETURN_DISPUTED':
      case 'HANDOVER_DISPUTED':
        // RETURN_DISPUTED freezes the unit BORROWED; HANDOVER_DISPUTED already
        // released it. The RETURN_DISPUTED unit is restored by the DB task.
        if (txn.state === 'RETURN_DISPUTED') {
          cy.task('restoreReturnDispute', txn.id)
        }
        break
      default:
        break
    }
  }

  const restoreMarkerInventory = () => {
    loginViaApi(ahmed)
    cy.request('GET', '/api/me/lend-requests').then((res) => {
      res.body
        .filter((t) => t.purpose.startsWith(marker))
        .forEach((txn) => closeMarkerTxn(txn))
    })
  }

  before(() => {
    marker = `ReturnDispute-${Date.now()}`
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
    restoreMarkerInventory()
  })

  after(() => {
    restoreMarkerInventory()
    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })
  })

  it('lender disputing the return freezes the loan read-only and keeps the unit borrowed', () => {
    // ── Create + approve + handover → ACTIVE ────────────────────────
    let txnId
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} Return dispute`, requestedDurationDays: 2 },
    }).then((res) => {
      txnId = res.body.id
    })
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)
    loginViaApi(ahmed)
    uploadPhoto(() => txnId, 'LENDER_HANDOVER')
    post(() => `/api/transactions/${txnId}/confirm-handover`)

    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(0)
      expect(borrowed).to.equal(1)
    })

    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/initiate-return`)

    // ── Borrower UI: photos are required, handback is gated until both ──
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains('Return in progress').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-body').should('be.visible')
    cy.get('.conversation-return-action button').contains("I've handed the item back").should('be.disabled')
    cy.get('.conversation-return-action input[type="file"]').eq(0).selectFile('cypress/fixtures/photo.png', { force: true })
    cy.get('.conversation-return-action button').contains('Before-return photo added', { timeout: 15000 }).scrollIntoView().should('be.visible')
    cy.get('.conversation-return-action input[type="file"]').eq(1).selectFile('cypress/fixtures/photo.png', { force: true })
    cy.get('.conversation-return-action button').contains('Handover photo added', { timeout: 15000 }).scrollIntoView().should('be.visible')
    cy.get('.conversation-return-action button').contains("I've handed the item back").should('be.enabled').scrollIntoView().click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Handback reported').should('be.visible')
    cy.get('.conversation-return-status').contains('Handback reported. Awaiting the owner').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    // ── Lender UI: return-side evidence is visible to the lender ──────
    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains('Return reported').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-return-action').contains('The borrower reported the item is back.').should('be.visible')
    cy.get('.conversation-return-action button').contains('Confirm received').scrollIntoView().should('be.visible')
    cy.get('.conversation-return-action button').contains('Not received').should('be.visible')
    cy.get('.conversation-return-title').contains('Photos').should('be.visible')
    cy.get('.conversation-evidence-item', { timeout: 15000 }).should('have.length', 3)
    cy.get('.conversation-evidence-item').first().find('img').should('be.visible')

    // ── RETURN_REPORTED guard rails: borrower can neither confirm nor dispute ──
    loginViaApi(salah)
    cy.wrap(null)
      .then(() =>
        cy.request({ method: 'POST', url: `/api/transactions/${txnId}/confirm-return`, failOnStatusCode: false }),
      )
      .then((res) => {
        expect(res.status).to.equal(401)
      })
    cy.wrap(null)
      .then(() =>
        cy.request({ method: 'POST', url: `/api/transactions/${txnId}/dispute-return`, failOnStatusCode: false }),
      )
      .then((res) => {
        expect(res.status).to.equal(401)
      })

    // ── Lender disputes via the conversation → RETURN_DISPUTED ────────
    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-return-action button').contains('Not received').click()
    cy.get('.conversation-system-body', { timeout: 15000 })
      .contains('Return disputed')
      .scrollIntoView()
      .should('be.visible')
    cy.get('.conversation-return-status')
      .contains('Return disputed. The loan has been frozen for review')
      .scrollIntoView()
      .should('be.visible')
    cy.get('.conversation-return-action').should('not.exist')
    cy.get('.conversation-composer').should('not.exist')
    cy.get('.conversation-evidence-item').should('have.length', 3)
    cy.get('button[aria-label="Close"]').click()

    // ── Loans UI shows the terminal dispute state ──
    cy.get('.transaction-card', { timeout: 15000 }).contains('Return disputed').should('be.visible')
    cy.get('.transaction-card').contains('button', 'View conversation').should('be.visible')

    // ── Final API assertions: frozen, read-only, unit stays BORROWED ──
    cy.wrap(null)
      .then(() => cy.request('GET', `/api/transactions/${txnId}`))
      .then((res) => {
        expect(res.body.state).to.equal('RETURN_DISPUTED')
        expect(res.body.returnDisputedAt).to.not.equal(null)
        expect(res.body.reservationHeld).to.equal(true)
      })

    cy.wrap(null)
      .then(() => cy.request('GET', `/api/transactions/${txnId}/messages`))
      .then((res) => {
        const event = res.body.find((m) => m.body === 'Return disputed')
        expect(event).to.exist
        expect(event.kind).to.equal('SYSTEM')
        expect(event.authorId).to.equal(null)
      })

    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(0)
      expect(borrowed).to.equal(1)
    })

    // ── RETURN_DISPUTED has no forward transitions and no messaging ──
    loginViaApi(ahmed)
    cy.wrap(null)
      .then(() =>
        cy.request({ method: 'POST', url: `/api/transactions/${txnId}/confirm-return`, failOnStatusCode: false }),
      )
      .then((res) => {
        expect(res.status).to.equal(400)
      })
    loginViaApi(salah)
    cy.wrap(null)
      .then(() =>
        cy.request({ method: 'POST', url: `/api/transactions/${txnId}/initiate-return`, failOnStatusCode: false }),
      )
      .then((res) => {
        expect(res.status).to.equal(400)
      })
    cy.wrap(null)
      .then(() =>
        cy.request({ method: 'POST', url: `/api/transactions/${txnId}/report-handback`, failOnStatusCode: false }),
      )
      .then((res) => {
        expect(res.status).to.equal(400)
      })
    cy.wrap(null)
      .then(() =>
        cy.request({
          method: 'POST',
          url: `/api/transactions/${txnId}/messages`,
          body: { body: 'too late' },
          failOnStatusCode: false,
        }),
      )
      .then((res) => {
        expect(res.status).to.equal(400)
      })
  })
})
