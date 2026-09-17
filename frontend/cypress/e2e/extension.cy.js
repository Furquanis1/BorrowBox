/**
 * V2.2.5 Loan Extensions Coverage
 *
 * Drives ACTIVE loans through the due-date extension negotiation added on top
 * of the transaction conversation (V2.2.5): the borrower requests a new due
 * date, and the lender can accept, reject or counter it. If countered, the
 * borrower can accept or decline the counter offer. Extensions never touch the
 * availability counts or the reservation lifecycle; dueAt moves only when an
 * offer is ultimately accepted and originalDueAt is preserved as an audit
 * anchor for the original loan.
 *
 * All transactions created here carry a unique purpose marker; the `after`
 * hook closes or completes every one of them so the canonical seeded fixture
 * (Football 2 units: 1 AVAILABLE + 1 RESERVED, 0 borrowed) is restored for the
 * rest of the test suite regardless of where the spec stopped.
 */
describe('V2.2.5 Loan Extensions', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }

  let marker
  let cseId
  let listingId

  const post = (buildPath, body) =>
    cy.wrap(null).then(() => cy.request({ method: 'POST', url: buildPath(), body }))
  const get = (buildPath) => cy.wrap(null).then(() => cy.request('GET', buildPath()))
  const fails = (buildPath, body) =>
    cy.wrap(null).then(() =>
      cy.request({ method: 'POST', url: buildPath(), body, failOnStatusCode: false }),
    )

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

  // V2.2.6: system-issued evidence uploads; binary content is arbitrary
  // (only content-type + size are validated by the backend).
  const uploadPhoto = (txnId, type) =>
    cy.wrap(null).then(() => {
      const form = new FormData()
      form.append('type', type)
      form.append('file', new Blob([new Uint8Array([137, 80, 78, 71, 1, 2, 3, 4])], { type: 'image/png' }), 'photo.png')
      return cy.request({ method: 'POST', url: `/api/transactions/${txnId}/evidence`, body: form, failOnStatusCode: true })
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

  const iso = (date) => date.toISOString().slice(0, 19)
  const sec = (date) => Math.floor(new Date(date).getTime() / 1000)
  const dtLocal = (isoStr) => {
    const d = isoStr ? new Date(isoStr) : new Date()
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
  }

  // React controlled inputs are set deterministically via the DOM value +
  // native input event rather than keystrokes, which is safer for
  // <input type="datetime-local"> segment parsing across browsers.
  const setDateTime = (selector, value) => {
    cy.get(selector).then(($el) => {
      const input = $el[0]
      const nativeSetter = Object.getOwnPropertyDescriptor(
        window.HTMLInputElement.prototype,
        'value',
      ).set
      nativeSetter.call(input, value)
      input.dispatchEvent(new Event('input', { bubbles: true }))
    })
  }

  const resolvePendingExtension = (txn) => {
    if (txn.extensionRequestPending) {
      loginViaApi(ahmed)
      cy.request('POST', `/api/transactions/${txn.id}/extension-reject`)
    }
    if (txn.extensionCounterPending) {
      loginViaApi(salah)
      cy.request('POST', `/api/transactions/${txn.id}/extension-counter-reject`)
    }
  }

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
        resolvePendingExtension(txn)
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
        // V2.2.4/5: the unit was already released back to AVAILABLE and the
        // extension negotiation was cleared; nothing to clean up.
        break
      case 'RETURN_DISPUTED':
        // V2.2.6: the unit is frozen BORROWED; no product API reverses it.
        cy.task('restoreReturnDispute', txn.id)
        break
      default:
        break
    }
  }

  before(() => {
    marker = `Extension-${Date.now()}`
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

  it('borrower requests, lender counters, borrower accepts the counter', () => {
    let txnId
    let dueAt
    // Eager-safe date helpers: the extended due dates are built from
    // Date.now() (not the API dueAt) so they are available to the UI
    // setters and request bodies, which evaluate their arguments before the
    // confirm-handover response resolves. Seconds are zeroed so the
    // minute-granular datetime-local value the UI submits round-trips to
    // exactly the same ISO string the backend stores. The confirmed dueAt ~=
    // now + 2d, so the requested/countered dates stay strictly after it with
    // wide margins.
    const align = (ms) => {
      const d = new Date(ms)
      d.setSeconds(0, 0)
      return iso(d)
    }
    const requestedDueAt = align(Date.now() + 4 * 86400000)
    const offeredDueAt = align(Date.now() + 3 * 86400000)
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} Counter flow`, requestedDurationDays: 2 },
    }).then((res) => {
      txnId = res.body.id
    })

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/confirm-handover`).then((res) => {
      expect(res.body.state).to.equal('ACTIVE')
      dueAt = res.body.dueAt
      expect(res.body.extensionRequestPending).to.equal(false)
    })

    // ── Borrower UI: request the extension from the conversation ──
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.get('.active-loans').should('be.visible')
    cy.get('.transaction-card', { timeout: 15000 }).contains('On loan').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-body', { timeout: 15000 }).should('be.visible')

    cy.get('.conversation-extension-action').contains('Need more time?').should('be.visible')
    cy.get('.conversation-extension-action button').contains('Need more time?').click()
    cy.get('.conversation-extension-action').scrollIntoView()
    cy.get('.conversation-extension-action [type="datetime-local"]').should('be.visible')
    setDateTime('.conversation-extension-action [type="datetime-local"]', dtLocal(requestedDueAt))
    cy.get('.conversation-extension-action button').contains('Request extension').click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Extension requested').should('be.visible')
    cy.get('.conversation-extension-status').contains('Extension request sent to the owner').should('be.visible')
    cy.get('.conversation-return-action button').contains('Start return').should('not.exist')
    cy.get('button[aria-label="Close"]').click()

    get(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.body.extensionRequestPending).to.equal(true)
      expect(res.body.extensionRequestedDueAt).to.equal(requestedDueAt)
      expect(sec(res.body.dueAt)).to.equal(sec(dueAt))
    })

    // ── Lender UI: counter the request from the conversation ──
    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains('On loan').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-extension-action').contains('Extension requested').should('be.visible')
    cy.get('.conversation-extension-action button').contains('Counter offer').click()
    cy.get('.conversation-extension-counter').should('be.visible')
    setDateTime('.conversation-extension-counter [type="datetime-local"]', dtLocal(offeredDueAt))
    cy.get('.conversation-extension-counter button').contains('Send counter offer').click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Extension countered').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    get(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.body.extensionCounterPending).to.equal(true)
      expect(res.body.extensionRequestPending).to.equal(false)
      expect(res.body.extensionOfferedDueAt).to.equal(offeredDueAt)
      expect(sec(res.body.dueAt)).to.equal(sec(dueAt))
    })

    // ── Borrower UI: accept the counter offer from the conversation ──
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains('On loan').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-extension-action').contains('Counter offer received').should('be.visible')
    cy.get('.conversation-extension-action button').contains('Accept counter').click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Extension counter accepted').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    // ── Final API assertions ─────────────────────────────────────
    get(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.body.state).to.equal('ACTIVE')
      expect(res.body.dueAt).to.equal(offeredDueAt)
      expect(sec(res.body.originalDueAt)).to.equal(sec(dueAt))
      expect(res.body.extensionRequestedDueAt).to.equal(null)
      expect(res.body.extensionOfferedDueAt).to.equal(null)
      expect(res.body.extensionRequestedAt).to.equal(null)
      expect(res.body.extensionRequestPending).to.equal(false)
      expect(res.body.extensionCounterPending).to.equal(false)
    })

    get(() => `/api/transactions/${txnId}/messages`).then((res) => {
      const bodies = res.body.map((m) => m.body)
      expect(bodies).to.include('Extension requested')
      expect(bodies).to.include('Extension countered')
      expect(bodies).to.include('Extension counter accepted')
    })

    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(0)
      expect(borrowed).to.equal(1)
    })
  })

  it('lender accepts the borrower request directly from the conversation', () => {
    let txnId
    let dueAt
    const align = (ms) => {
      const d = new Date(ms)
      d.setSeconds(0, 0)
      return iso(d)
    }
    const requestedDueAt = align(Date.now() + 4 * 86400000)
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} Accept flow`, requestedDurationDays: 2 },
    }).then((res) => {
      txnId = res.body.id
    })

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/confirm-handover`).then((res) => {
      expect(res.body.state).to.equal('ACTIVE')
      dueAt = res.body.dueAt
    })

    // ── Borrower UI: request ─────────────────────────────────────
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains('On loan').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-extension-action button').contains('Need more time?').should('be.visible')
    cy.get('.conversation-extension-action button').contains('Need more time?').click()
    cy.get('.conversation-extension-action').scrollIntoView()
    setDateTime('.conversation-extension-action [type="datetime-local"]', dtLocal(requestedDueAt))
    cy.get('.conversation-extension-action button').contains('Request extension').click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Extension requested').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    // ── Lender UI: accept directly ───────────────────────────────
    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains('On loan').should('be.visible')
    cy.get('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-extension-action').contains('Extension requested').should('be.visible')
    cy.get('.conversation-extension-action button').contains('Accept').click()
    cy.get('.conversation-system-body', { timeout: 15000 }).contains('Extension approved').should('be.visible')
    cy.get('button[aria-label="Close"]').click()

    get(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.body.state).to.equal('ACTIVE')
      expect(res.body.dueAt).to.equal(requestedDueAt)
      expect(sec(res.body.originalDueAt)).to.equal(sec(dueAt))
      expect(res.body.extensionRequestedDueAt).to.equal(null)
      expect(res.body.extensionRequestedAt).to.equal(null)
    })
  })

  it('enforces extension date, role, and lifecycle guard rails', () => {
    let txnId
    let dueAt
    const requestedDueAt = iso(new Date(Date.now() + 4 * 86400000))
    const beyondWindowDueAt = iso(new Date(Date.now() + 40 * 86400000))
    const nearerDueAt = iso(new Date(Date.now() + 3 * 86400000))
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} Guard rails`, requestedDurationDays: 2 },
    }).then((res) => {
      txnId = res.body.id
    })

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/confirm-handover`).then((res) => {
      dueAt = res.body.dueAt
    })

    // A new due date must be in the future and strictly after the current due.
    loginViaApi(salah)
    fails(() => `/api/transactions/${txnId}/extension-request`,
      { newDueAt: iso(new Date(Date.now() - 86400000)), note: 'past' }).then((res) => {
      expect(res.status).to.equal(400)
    })

    // ...and no more than 30 days past the current due date.
    fails(() => `/api/transactions/${txnId}/extension-request`,
      { newDueAt: beyondWindowDueAt }).then((res) => {
      expect(res.status).to.equal(400)
    })

    // A valid request lands; only one may be pending at a time.
    post(() => `/api/transactions/${txnId}/extension-request`,
      { newDueAt: requestedDueAt, note: 'Need the unit longer' }).then((res) => {
      expect(res.body.extensionRequestPending).to.equal(true)
    })
    fails(() => `/api/transactions/${txnId}/extension-request`, { newDueAt: requestedDueAt }).then((res) => {
      expect(res.status).to.equal(400)
    })

    // Return cannot start while the extension is still being negotiated.
    loginViaApi(salah)
    fails(() => `/api/transactions/${txnId}/initiate-return`).then((res) => {
      expect(res.status).to.equal(400)
    })

    // Roles are locked: the requester cannot accept, and the lender cannot request.
    loginViaApi(salah)
    fails(() => `/api/transactions/${txnId}/extension-accept`).then((res) => {
      expect(res.status).to.equal(401)
    })
    loginViaApi(ahmed)
    fails(() => `/api/transactions/${txnId}/extension-request`, { newDueAt: requestedDueAt }).then((res) => {
      expect(res.status).to.equal(401)
    })

    // The lender can reject; dueAt is untouched and the negotiation is cleared.
    post(() => `/api/transactions/${txnId}/extension-reject`).then((res) => {
      expect(sec(res.body.dueAt)).to.equal(sec(dueAt))
      expect(res.body.extensionRequestedDueAt).to.equal(null)
    })

    // Counter offers obey the same date rules and are lender-only.
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/extension-request`, { newDueAt: requestedDueAt }).then((res) => {
      expect(res.body.extensionRequestPending).to.equal(true)
    })
    loginViaApi(ahmed)
    fails(() => `/api/transactions/${txnId}/extension-counter`,
      { newDueAt: beyondWindowDueAt }).then((res) => {
      expect(res.status).to.equal(400)
    })
    post(() => `/api/transactions/${txnId}/extension-counter`,
      { newDueAt: nearerDueAt, note: 'One more day' }).then((res) => {
      expect(res.body.extensionCounterPending).to.equal(true)
    })

    // The borrower can decline the counter; dueAt stays put.
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/extension-counter-reject`).then((res) => {
      expect(sec(res.body.dueAt)).to.equal(sec(dueAt))
      expect(res.body.extensionRequestedAt).to.equal(null)
    })

    // A fresh request followed by a handover dispute clears the negotiation
    // atomically: the dispute snapshot carries no extension fields.
    post(() => `/api/transactions/${txnId}/extension-request`, { newDueAt: requestedDueAt }).then((res) => {
      expect(res.body.extensionRequestPending).to.equal(true)
    })
    loginViaApi(salah)
    fails(() => `/api/transactions/${txnId}/initiate-return`).then((res) => {
      expect(res.status).to.equal(400)
    })
    post(() => `/api/transactions/${txnId}/dispute-handover`).then((res) => {
      expect(res.body.state).to.equal('HANDOVER_DISPUTED')
      expect(res.body.extensionRequestPending).to.equal(false)
      expect(res.body.extensionCounterPending).to.equal(false)
      expect(res.body.extensionRequestedDueAt).to.equal(null)
      expect(res.body.extensionOfferedDueAt).to.equal(null)
    })

    get(() => `/api/transactions/${txnId}/messages`).then((res) => {
      const bodies = res.body.map((m) => m.body)
      expect(bodies).to.include('Extension requested')
      expect(bodies).to.include('Extension countered')
      expect(bodies).to.include('Extension counter rejected')
      expect(bodies).to.include('Handover disputed')
    })

    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })
  })
})