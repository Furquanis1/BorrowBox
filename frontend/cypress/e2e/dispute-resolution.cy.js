const { evidencePhotoBlob } = require('../support/evidencePhoto')

/**
 * Manager dispute resolution coverage.
 *
 * Drives a Football loan through RETURN_DISPUTED, verifies that participants and
 * managers of other communities cannot resolve it, then resolves it from the
 * manager-only flags queue. The resolution must be visible as a durable SYSTEM
 * conversation entry and timeline event, move the loan to COMPLETED, release the
 * unit back to AVAILABLE, notify both participants, and reject a second attempt.
 */
describe('Manager dispute resolution', () => {
  const ahmed = { email: 'ahmed@example.com', password: Cypress.env('seedPassword') }
  const salah = { email: 'salah@example.com', password: Cypress.env('seedPassword') }
  const omar = { email: 'omar@example.com', password: Cypress.env('seedPassword') }

  let marker
  let cseId
  let listingId

  const loginViaApi = (user) =>
    cy.request({ method: 'POST', url: '/api/auth/login', body: user, failOnStatusCode: true })

  const loginViaUi = (user) => {
    cy.clearCookies()
    cy.clearLocalStorage()
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').type(user.email)
    cy.get('input[type="password"]').type(user.password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/communities/', { timeout: 15000 })
    cy.request({ method: 'POST', url: '/api/me/events/read-all' })
  }

  const footballCounts = () =>
    cy.wrap(null).then(() =>
      cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
        const football = res.body.find((l) => l.title === 'Football')
        return { available: football.availableUnits, borrowed: football.borrowedUnits }
      }),
    )

  const buildUrl = (path) => (typeof path === 'function' ? path() : path)

  const post = (path, options = {}) =>
    cy.wrap(null).then(() =>
      cy.request({
        method: 'POST',
        url: buildUrl(path),
        failOnStatusCode: true,
        ...options,
      }),
    )

  const get = (path, options = {}) =>
    cy.wrap(null).then(() =>
      cy.request({
        method: 'GET',
        url: buildUrl(path),
        failOnStatusCode: true,
        ...options,
      }),
    )

  const uploadPhoto = (type) =>
    cy.wrap(null).then(() => {
      const form = new FormData()
      form.append('type', type)
      form.append('file', evidencePhotoBlob(), 'photo.png')
      return cy.request({
        method: 'POST',
        url: `/api/transactions/${Cypress.env('disputeTxnId')}/evidence`,
        body: form,
        failOnStatusCode: true,
      })
    })

  const resolutionPayload = (note) => ({
    outcome: 'FAVOR_BORROWER',
    resolutionNote: note,
    unitCondition: 'INTACT',
    freeUnit: true,
  })

  before(() => {
    marker = `DisputeResolution-${Date.now()}`
    loginViaApi(ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
    })
    cy.then(() =>
      cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
        listingId = res.body.find((l) => l.title === 'Football').id
      }),
    )
  })

  after(() => {
    loginViaApi(ahmed)
    cy.request('GET', '/api/me/lend-requests').then((res) => {
      res.body
        .filter((txn) => txn.purpose.startsWith(marker))
        .forEach((txn) => cy.task('restoreReturnDispute', txn.id))
    })
    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })
  })

  it('resolves a return dispute from the manager queue and records the decision', () => {
    let txnId

    // ── Create a loan and drive it to RETURN_DISPUTED ──────────────────
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} return dispute`, requestedDurationDays: 2 },
      failOnStatusCode: true,
    }).then((res) => {
      txnId = res.body.id
      Cypress.env('disputeTxnId', txnId)
    })

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)
    loginViaApi(ahmed)
    uploadPhoto('LENDER_HANDOVER')
    post(() => `/api/transactions/${txnId}/confirm-handover`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/initiate-return`)
    uploadPhoto('BORROWER_PRE_RETURN')
    uploadPhoto('BORROWER_RETURN_HANDOVER')
    post(() => `/api/transactions/${txnId}/report-handback`)
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/dispute-return`)

    get(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.body.state).to.equal('RETURN_DISPUTED')
    })
    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(0)
      expect(borrowed).to.equal(1)
    })

    // ── Only an active manager of this community may resolve ──────────
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/resolve-dispute`, {
      body: resolutionPayload(`${marker} denied participant`),
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.equal(403)
    })

    loginViaApi(omar)
    post(() => `/api/transactions/${txnId}/resolve-dispute`, {
      body: resolutionPayload(`${marker} denied other manager`),
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.equal(403)
    })

    // ── Manager queue: read-only conversation, evidence and timeline ───
    loginViaUi(ahmed)
    cy.visit(`/communities/${cseId}/flags`)
    cy.get('.dispute-queue', { timeout: 15000 }).should('be.visible')
    cy.contains('.dispute-item', marker, { timeout: 15000 }).should('be.visible')
    cy.contains('.dispute-item', marker).contains('button', 'Review dispute').click()

    cy.get('.conversation-manager-review', { timeout: 15000 }).scrollIntoView().should('be.visible')
    cy.get('.conversation-system-body', { timeout: 15000 })
      .contains('Return disputed')
      .scrollIntoView()
      .should('be.visible')
    cy.get('.conversation-evidence-slots .conversation-evidence-item', { timeout: 15000 }).should(
      'have.length',
      3,
    )
    cy.contains('button', 'Activity timeline').click()
    cy.get('.timeline-title', { timeout: 15000 })
      .contains('Return disputed')
      .scrollIntoView()
      .should('be.visible')

    // ── Record the decision in the manager UI ──────────────────────────
    const note = `${marker} manager decision`
    cy.get('#resolution-outcome').select('FAVOR_BORROWER')
    cy.get('#resolution-note').type(note)
    cy.contains('.dispute-resolution button', 'Resolve dispute').click()

    cy.get('.conversation-system-body', { timeout: 15000 })
      .contains(`Dispute resolved: FAVOR_BORROWER - ${note}`)
      .scrollIntoView()
      .should('be.visible')
    cy.get('.timeline-title', { timeout: 15000 })
      .contains('Dispute resolved')
      .scrollIntoView()
      .should('be.visible')

    cy.get('button[aria-label="Close"]').click()
    cy.get('.dispute-queue', { timeout: 15000 }).should('not.contain', marker)

    // ── Durable outcome: state, inventory, notifications, idempotence ───
    get(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.body.state).to.equal('COMPLETED')
      expect(res.body.completedAt).to.not.equal(null)
    })
    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })

    loginViaApi(salah)
    cy.request('GET', '/api/me/notifications').then((res) => {
      const notification = res.body.find(
        (item) => item.type === 'DISPUTE_RESOLVED' && item.message.includes(note),
      )
      expect(notification).to.exist
      expect(notification.transactionId).to.equal(txnId)
    })

    loginViaApi(ahmed)
    cy.request('GET', '/api/me/notifications').then((res) => {
      const notification = res.body.find(
        (item) => item.type === 'DISPUTE_RESOLVED' && item.message.includes(note),
      )
      expect(notification).to.exist
      expect(notification.transactionId).to.equal(txnId)
    })

    post(() => `/api/transactions/${txnId}/resolve-dispute`, {
      body: resolutionPayload(note),
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.equal(400)
    })
  })
})
