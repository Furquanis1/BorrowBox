// V2.5.1: the lender captures a pre-lending photo and a handover photo before
// confirming the handover. Optional condition note + rating are stored per
// evidence row, shown to both participants, and the handover cannot be
// confirmed without the LENDER_HANDOVER photo.
describe('Lender handover evidence (V2.5.1)', () => {
  let marker
  let cseId
  let listingId

  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }
  const omar = { email: 'omar@example.com', password: 'password123' }

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
    // V2.2.8: mark deliveries READ so the Event Envelope does not overlay the UI.
    cy.request({ method: 'POST', url: '/api/me/events/read-all' })
  }

  const resolveUrl = (build) => (typeof build === 'function' ? build() : build)

  const post = (build) => cy.wrap(null).then(() => cy.request({ method: 'POST', url: resolveUrl(build) }))

  const get = (build) => cy.wrap(null).then(() => cy.request({ method: 'GET', url: resolveUrl(build) }))

  const photoForm = (type, conditionNote, conditionRating) => {
    const form = new FormData()
    form.append('type', type)
    form.append('file', new Blob([new Uint8Array([137, 80, 78, 71, 1, 2, 3, 4])], { type: 'image/png' }), 'photo.png')
    if (conditionNote != null) form.append('conditionNote', conditionNote)
    if (conditionRating != null) form.append('conditionRating', String(conditionRating))
    return form
  }

  const uploadPhoto = (buildTxnId, type, conditionNote, conditionRating) =>
    cy.wrap(null).then(() => {
      const id = typeof buildTxnId === 'function' ? buildTxnId() : buildTxnId
      return cy.request({
        method: 'POST',
        url: `/api/transactions/${id}/evidence`,
        body: photoForm(type, conditionNote, conditionRating),
        failOnStatusCode: true,
      })
    })

  const uploadReturnEvidence = (id) => {
    uploadPhoto(id, 'BORROWER_PRE_RETURN')
    uploadPhoto(id, 'BORROWER_RETURN_HANDOVER')
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
        post(`/api/transactions/${txn.id}/cancel`)
        break
      case 'AWAITING_HANDOVER':
      case 'APPROVED':
        loginViaApi(ahmed)
        post(`/api/transactions/${txn.id}/cancel`)
        break
      case 'ACTIVE':
        loginViaApi(salah)
        post(`/api/transactions/${txn.id}/initiate-return`)
        uploadReturnEvidence(txn.id)
        post(`/api/transactions/${txn.id}/report-handback`)
        loginViaApi(ahmed)
        post(`/api/transactions/${txn.id}/confirm-return`)
        break
      case 'RETURN_INITIATED':
        loginViaApi(salah)
        uploadReturnEvidence(txn.id)
        post(`/api/transactions/${txn.id}/report-handback`)
        loginViaApi(ahmed)
        post(`/api/transactions/${txn.id}/confirm-return`)
        break
      case 'RETURN_REPORTED':
        loginViaApi(ahmed)
        post(`/api/transactions/${txn.id}/confirm-return`)
        break
      default:
        break
    }
  }

  before(() => {
    marker = `LenderEvidence-${Date.now()}`
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

  it('pre-lending + handover photos with condition metadata gate and outlive the handover', () => {
    let txnId
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} Lender evidence`, requestedDurationDays: 2 },
    }).then((res) => {
      txnId = res.body.id
    })
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)

    // ── Confirm handover is gated on the LENDER_HANDOVER photo ──────
    loginViaApi(ahmed)
    cy.wrap(null)
      .then(() =>
        cy.request({
          method: 'POST',
          url: `/api/transactions/${txnId}/confirm-handover`,
          failOnStatusCode: false,
        }),
      )
      .then((res) => {
        expect(res.status).to.equal(400)
        expect(res.body.error).to.equal('Handover evidence required before confirming handover')
      })

    // ── Authorization: borrower cannot upload lender evidence ───────
    loginViaApi(salah)
    cy.wrap(null)
      .then(() =>
        cy.request({
          method: 'POST',
          url: `/api/transactions/${txnId}/evidence`,
          body: photoForm('LENDER_PRE_LENDING'),
          failOnStatusCode: false,
        }),
      )
      .then((res) => {
        expect(res.status).to.equal(401)
      })

    // ── Authorization: a non-participant cannot upload or view ──────
    loginViaApi(omar)
    cy.wrap(null)
      .then(() =>
        cy.request({
          method: 'POST',
          url: `/api/transactions/${txnId}/evidence`,
          body: photoForm('LENDER_HANDOVER'),
          failOnStatusCode: false,
        }),
      )
      .then((res) => {
        expect(res.status).to.equal(401)
      })
    cy.wrap(null)
      .then(() =>
        cy.request({ method: 'GET', url: `/api/transactions/${txnId}/evidence`, failOnStatusCode: false }),
      )
      .then((res) => {
        expect(res.status).to.equal(401)
      })

    // ── Lender UI: pre-lend + handover slots in AWAITING_HANDOVER ──
    loginViaUi(ahmed)
    cy.visit('/me/requests')
    cy.url({ timeout: 15000 }).should('include', '/me/requests')
    cy.get('.requests-tab').contains('Incoming').click()
    cy.get('.transaction-card').contains(marker).closest('.transaction-card').contains('Awaiting handover').should('be.visible')
    cy.get('.transaction-card').contains(marker).closest('.transaction-card').contains('button', 'Discuss pickup').click()
    cy.get('.conversation-body').should('be.visible')
    cy.get('.conversation-lender-evidence').should('be.visible')
    cy.get('.conversation-lender-evidence button').contains('Add pre-lend photo').should('be.visible')
    cy.get('.conversation-lender-evidence button').contains('Add handover photo').should('be.visible')
    cy.get('.conversation-lender-evidence').contains('The handover photo is required before you can confirm the handover.')
      .should('be.visible')

    // Pre-lending photo with condition metadata.
    cy.get('.conversation-lender-evidence input[type="file"]').eq(0).selectFile('cypress/fixtures/photo.png', { force: true })
    cy.get('.conversation-evidence-pending').should('be.visible')
    cy.get('.conversation-evidence-pending textarea').type('Pre-lending: frame intact')
    cy.get('.conversation-evidence-pending select').select('3')
    cy.get('.conversation-evidence-pending button').contains('Upload photo').click()
    cy.get('.conversation-lender-evidence button').contains('Pre-lend photo added', { timeout: 15000 }).should('be.visible')

    // Handover photo with different condition metadata.
    cy.get('.conversation-lender-evidence input[type="file"]').eq(1).selectFile('cypress/fixtures/photo.png', { force: true })
    cy.get('.conversation-evidence-pending textarea').type('Handover: handed in good order')
    cy.get('.conversation-evidence-pending select').select('4')
    cy.get('.conversation-evidence-pending button').contains('Upload photo').click()
    cy.get('.conversation-lender-evidence button').contains('Handover photo added', { timeout: 15000 }).should('be.visible')
    cy.get('.conversation-lender-evidence')
      .contains('The handover photo is required before you can confirm the handover.')
      .should('not.exist')

    // The photos + condition metadata are visible right away in the drawer.
    cy.get('.conversation-evidence-item', { timeout: 15000 }).should('have.length', 2)
    cy.get('.conversation-evidence-item').contains('Pre-lending · Ahmed · Pre-lending: frame intact · 3/5').should('be.visible')
    cy.get('.conversation-evidence-item').contains('At handover · Ahmed · Handover: handed in good order · 4/5').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    // ── Confirm handover now that the evidence exists ──
    cy.get('.transaction-card').contains(marker).closest('.transaction-card').contains('button', 'Confirm handover').click()
    get(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.body.state).to.equal('ACTIVE')
    })

    // ── Once ACTIVE the upload controls disappear ──
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains('On loan').should('be.visible')
    cy.get('.transaction-card').contains(marker).closest('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-lender-evidence').should('not.exist')
    cy.get('button[aria-label="Close"]').click()

    // ── Metadata persists per evidence row, with the right type ──
    get(() => `/api/transactions/${txnId}/evidence`).then((res) => {
      const pre = res.body.find((e) => e.type === 'LENDER_PRE_LENDING')
      const hand = res.body.find((e) => e.type === 'LENDER_HANDOVER')
      expect(pre.conditionNote).to.equal('Pre-lending: frame intact')
      expect(pre.conditionRating).to.equal(3)
      expect(hand.conditionNote).to.equal('Handover: handed in good order')
      expect(hand.conditionRating).to.equal(4)
      expect(res.body.every((e) => e.capturerName === 'Ahmed')).to.equal(true)
    })

    // ── Return window: the lender's photos remain visible to the borrower ──
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/initiate-return`)
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains('Return in progress').should('be.visible')
    cy.get('.transaction-card').contains(marker).closest('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-evidence-item', { timeout: 15000 }).should('have.length', 2)
    cy.get('.conversation-evidence-item').contains('Pre-lending · Ahmed · Pre-lending: frame intact · 3/5').should('be.visible')
    cy.get('.conversation-evidence-item').contains('At handover · Ahmed · Handover: handed in good order · 4/5').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    // ── Borrower return flow still works end to end ──
    loginViaApi(salah)
    uploadReturnEvidence(() => txnId)
    post(() => `/api/transactions/${txnId}/report-handback`)
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/confirm-return`)
    get(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.body.state).to.equal('COMPLETED')
    })

    // Both participants see the identical, complete evidence set afterwards.
    loginViaApi(salah)
    get(() => `/api/transactions/${txnId}/evidence`).then((res) => {
      const types = res.body.map((e) => e.type)
      expect(types).to.deep.equal([
        'LENDER_PRE_LENDING',
        'LENDER_HANDOVER',
        'BORROWER_PRE_RETURN',
        'BORROWER_RETURN_HANDOVER',
      ])
    })

    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })
  })
})