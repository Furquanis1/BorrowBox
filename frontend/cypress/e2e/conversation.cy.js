/**
 * V2.2.3 Transaction Conversation Coverage
 *
 * Drives a marker transaction through the borrowed-life coordination phases
 * while exercising the per-transaction conversation for both participants:
 *
 *   PENDING → APPROVED → AWAITING_HANDOVER → ACTIVE → RETURN_INITIATED
 *     → RETURN_REPORTED → COMPLETED
 *
 * Verifies that USER messages from both parties and the SYSTEM timeline entries
 * produced by lifecycle transitions are visible to both participants, that
 * messaging works while AWAITING_HANDOVER and ACTIVE, and that a COMPLETED
 * conversation stays readable but its composer disappears (read-only archive).
 *
 * Every transaction carries a unique purpose marker so the `after` hook can
 * close/release it and restore the canonical seeded fixture.
 */
describe('V2.2.3 Transaction Conversation', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }

  let marker
  let cseId
  let listingId
  let txnId

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

  const listMessages = () =>
    cy.wrap(null).then(() => cy.request('GET', `/api/transactions/${txnId}/messages`))

  const systemBodies = (res) => res.body.filter((m) => m.kind === 'SYSTEM').map((m) => m.body)

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
    marker = `Conversation-${Date.now()}`
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
  })

  it('both participants coordinate pickup and see the lifecycle timeline', () => {
    // ── Create + approve ────────────────────────────────────────────
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} Gym pick-up`, requestedDurationDays: 2 },
    }).then((res) => {
      txnId = res.body.id
      expect(res.body.state).to.equal('PENDING')
    })

    loginViaApi(ahmed)
    cy.wrap(null).then(() =>
      cy.request({ method: 'POST', url: `/api/transactions/${txnId}/approve` }).then((res) => {
        expect(res.body.state).to.equal('APPROVED')
      }),
    )

    // ── Both participants message while APPROVED ──────────────────────
    loginViaApi(salah)
    cy.wrap(null).then(() =>
      cy.request({
        method: 'POST',
        url: `/api/transactions/${txnId}/messages`,
        body: { body: 'Can you drop it at the gym reception?' },
      }).then((res) => {
        expect(res.status).to.equal(201)
        expect(res.body.kind).to.equal('USER')
        expect(res.body.authorName).to.equal('Salah')
        expect(res.body.authorId).to.not.equal(null)
      }),
    )

    loginViaApi(ahmed)
    cy.wrap(null).then(() =>
      cy.request({
        method: 'POST',
        url: `/api/transactions/${txnId}/messages`,
        body: { body: 'Sure, see you at 5pm' },
      }).then((res) => {
        expect(res.status).to.equal(201)
        expect(res.body.authorName).to.equal('Ahmed')
      }),
    )

    // ── Brandon: SYSTEM timeline entry appears with staged handover ───
    loginViaApi(salah)
    cy.wrap(null).then(() =>
      cy.request({ method: 'POST', url: `/api/transactions/${txnId}/stage-handover` }).then((res) => {
        expect(res.body.state).to.equal('AWAITING_HANDOVER')
      }),
    )

    listMessages().then((res) => {
      const system = systemBodies(res)
      expect(system).to.include('Handover scheduled')
      res.body.forEach((m) => {
        expect(m.transactionId).to.equal(txnId)
        expect(m.createdAt).to.not.equal(null)
      })
    })

    // ── Borrower sends another message while AWAITING_HANDOVER ────────
    loginViaApi(salah)
    cy.wrap(null).then(() =>
      cy.request({
        method: 'POST',
        url: `/api/transactions/${txnId}/messages`,
        body: { body: 'I am at the gate now' },
      }).then((res) => {
        expect(res.status).to.equal(201)
      }),
    )

    // ── Borrower UI: open the conversation and send a message ─────────
    loginViaUi(salah)
    cy.visit('/me/requests')
    cy.get('.requests-tab').contains('My requests').click()
    cy.get('.transaction-card').contains(marker).closest('.transaction-card').contains('button', 'Discuss pickup').click()
    cy.get('.conversation-body').should('be.visible')
    cy.get('.conversation-body').contains('Can you drop it at the gym reception?').should('be.visible')
    cy.get('.conversation-body').contains('Sure, see you at 5pm').should('be.visible')
    cy.get('.conversation-system-body').contains('Handover scheduled').should('be.visible')

    cy.get('.conversation-input').type('See you soon')
    cy.get('.conversation-composer button[type="submit"]').click()
    cy.get('.conversation-body', { timeout: 15000 }).contains('See you soon').should('be.visible')

// ── Move to ACTIVE; SYSTEM event visible on the lender side ───────
    loginViaApi(ahmed)
    cy.wrap(null).then(() =>
      cy.request({ method: 'POST', url: `/api/transactions/${txnId}/confirm-handover` }).then((res) => {
        expect(res.body.state).to.equal('ACTIVE')
      }),
    )

    // ── ACTIVE leaves the Requests inbox; Loans shows it onward ─────
    loginViaUi(salah)
    cy.visit('/me/requests')
    cy.get('.requests-tab').contains('My requests').click()
    cy.get('.transaction-card').should('not.contain', marker)

    // ── Lender UI: conversation still visible while ACTIVE; can reply ─
    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.get('.transaction-card').contains('On loan').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-system-body').contains('Loan started').should('be.visible')
    cy.get('.conversation-body').contains('See you soon').should('be.visible')

    cy.get('.conversation-input').type('Enjoy the match')
    cy.get('.conversation-composer button[type="submit"]').click()
    cy.get('.conversation-body', { timeout: 15000 }).contains('Enjoy the match').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    // ── Borrower UI: knows the plan on the loans page while ACTIVE ────
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-body').contains('Enjoy the match').should('be.visible')
    cy.get('.conversation-composer').should('be.visible')

    // ── Borrower starts the return from the conversation ─────────
    cy.get('.conversation-return-action').contains('Ready to return the item?').should('be.visible')
    cy.get('.conversation-return-action button').contains('Start return').click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Return initiated').should('be.visible')

    // RETURN_INITIATED: the borrower now reports the physical handback.
    cy.get('.conversation-return-action button').contains("I've handed the item back").should('be.visible')

    // ── RETURN_INITIATED guard rails at the API ────────────────────
    // The lender can neither complete the loan before the handback is
    // reported, nor report the handback on the borrower's behalf.
    loginViaApi(ahmed)
    cy.wrap(null).then(() =>
      cy.request({
        method: 'POST',
        url: `/api/transactions/${txnId}/confirm-return`,
        failOnStatusCode: false,
      }).then((res) => {
        expect(res.status).to.equal(400)
      }),
    )
    cy.wrap(null).then(() =>
      cy.request({
        method: 'POST',
        url: `/api/transactions/${txnId}/report-handback`,
        failOnStatusCode: false,
      }).then((res) => {
        expect(res.status).to.equal(401)
      }),
    )

    // ── Borrower reports the handback → RETURN_REPORTED ─────────────
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-return-action button').contains("I've handed the item back").click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Handback reported').should('be.visible')
    cy.get('.conversation-return-action').should('not.exist')
    cy.get('.conversation-return-status').contains('Handback reported. Awaiting the owner').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    // RETURN_REPORTED: the borrower still cannot confirm receipt.
    loginViaApi(salah)
    cy.wrap(null).then(() =>
      cy.request({
        method: 'POST',
        url: `/api/transactions/${txnId}/confirm-return`,
        failOnStatusCode: false,
      }).then((res) => {
        expect(res.status).to.equal(401)
      }),
    )

    // ── Lender confirms receipt from the conversation → COMPLETED ──
    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.get('.transaction-card').contains('Return reported').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-return-action').contains('The borrower reported the item is back.').should('be.visible')
    cy.get('.conversation-return-action button').contains('Confirm received').click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Loan completed').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    listMessages().then((res) => {
      expect(systemBodies(res)).to.deep.equal([
        'Handover scheduled',
        'Loan started',
        'Return initiated',
        'Handback reported',
        'Loan completed',
      ])
      expect(res.body).to.have.length(10)
      expect(res.body.filter((m) => m.kind === 'SYSTEM').every((m) => m.authorId === null)).to.equal(true)
      expect(res.body.filter((m) => m.kind === 'USER').every((m) => m.authorId !== null)).to.equal(true)
    })

    // Terminal conversation is readable but has no composer.
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.get('.transaction-card').contains('button', 'View conversation').click()
    cy.get('.conversation-system-body').contains('Loan completed').should('be.visible')
    cy.get('.conversation-body').contains('Sure, see you at 5pm').should('be.visible')
    cy.get('.conversation-composer').should('not.exist')

    // Writes are rejected at the API too.
    loginViaUi(salah)
    cy.wrap(null).then(() =>
      cy.request({
        method: 'POST',
        url: `/api/transactions/${txnId}/messages`,
        body: { body: 'too late' },
        failOnStatusCode: false,
      }).then((res) => {
        expect(res.status).to.equal(400)
      }),
    )
  })
})