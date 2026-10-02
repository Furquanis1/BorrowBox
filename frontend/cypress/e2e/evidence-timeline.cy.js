// V2.5.3: evidence presentation -- role-explicit moment labels, the capturedAt
// timestamp on every row, and the rendered sequence following the server's
// chronological order (capturedAt ASC, id ASC).
//
// Before this slice LENDER_HANDOVER and BORROWER_RETURN_HANDOVER both rendered as
// "At handover", so the two handover moments were indistinguishable and the
// capture time was never shown. These tests are the regression guard for that.
//
// Setup mirrors lender-evidence.cy.js: one transaction is driven through the full
// lifecycle via the product API until all four evidence moments exist, then the
// drawer is opened once and every assertion reads the same rendered DOM.
describe('Evidence labels, timestamps and ordering (V2.5.3)', () => {
  let marker
  let cseId
  let listingId
  let txnId

  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }

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
    cy.request({ method: 'POST', url: '/api/me/events/read-all' })
  }

  // Resolved lazily: txnId is assigned inside a .then() that has not run yet
  // when these call sites are built, so the URL must be a thunk. Same reason
  // lender-evidence.cy.js routes every id through resolveUrl.
  const resolveUrl = (build) => (typeof build === 'function' ? build() : build)

  const post = (build) => cy.wrap(null).then(() => cy.request({ method: 'POST', url: resolveUrl(build), failOnStatusCode: true }))

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
      const id = resolveUrl(buildTxnId)
      return cy.request({
        method: 'POST',
        url: `/api/transactions/${id}/evidence`,
        body: photoForm(type, conditionNote, conditionRating),
        failOnStatusCode: true,
      })
    })

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
        uploadPhoto(txn.id, 'BORROWER_PRE_RETURN')
        uploadPhoto(txn.id, 'BORROWER_RETURN_HANDOVER')
        post(`/api/transactions/${txn.id}/report-handback`)
        loginViaApi(ahmed)
        post(`/api/transactions/${txn.id}/confirm-return`)
        break
      case 'RETURN_INITIATED':
        loginViaApi(salah)
        uploadPhoto(txn.id, 'BORROWER_PRE_RETURN')
        uploadPhoto(txn.id, 'BORROWER_RETURN_HANDOVER')
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

  const closeAllMarkerTxns = () => {
    loginViaApi(ahmed)
    cy.request('GET', '/api/me/lend-requests').then((res) => {
      res.body
        .filter((t) => t.purpose.startsWith(marker))
        .forEach((txn) => closeMarkerTxn(txn))
    })
  }

  before(() => {
    marker = `EvidenceTimeline-${Date.now()}`
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

  beforeEach(() => {
    // Full lifecycle: lender evidence with condition metadata, then the borrower's
    // return evidence, stopping at RETURN_REPORTED. That state is the last one in
    // EVIDENCE_VISIBLE_STATES, so the drawer still renders all four moments.
    // COMPLETED is deliberately NOT used: the evidence gate does not include it,
    // and widening that gate is out of scope for this slice.
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} timeline`, requestedDurationDays: 2 },
      failOnStatusCode: true,
    }).then((res) => {
      txnId = res.body.id
    })

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)

    loginViaApi(ahmed)
    uploadPhoto(() => txnId, 'LENDER_PRE_LENDING', 'Pre-lending: frame intact', 3)
    uploadPhoto(() => txnId, 'LENDER_HANDOVER', 'Handover: handed in good order', 4)
    post(() => `/api/transactions/${txnId}/confirm-handover`)

    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/initiate-return`)
    uploadPhoto(() => txnId, 'BORROWER_PRE_RETURN')
    uploadPhoto(() => txnId, 'BORROWER_RETURN_HANDOVER')
    post(() => `/api/transactions/${txnId}/report-handback`)
  })

  afterEach(() => {
    closeAllMarkerTxns()
  })

  after(() => {
    closeAllMarkerTxns()
    loginViaApi(ahmed)
    cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
      const football = res.body.find((l) => l.title === 'Football')
      expect(football.availableUnits).to.equal(1)
      expect(football.borrowedUnits).to.equal(0)
    })
  })

  it('distinguishes the lender handover from the borrower return handover', () => {
    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains(marker).closest('.transaction-card').contains('button', 'Conversation').click()

    // All four moments are present, each with its own label.
    cy.get('.conversation-evidence-item', { timeout: 15000 }).should('have.length', 4)
    cy.get('.conversation-evidence-item').contains('Lender — Pre-lending').should('be.visible')
    cy.get('.conversation-evidence-item').contains('Lender — Handover').should('be.visible')
    cy.get('.conversation-evidence-item').contains('Borrower — Before return').should('be.visible')
    cy.get('.conversation-evidence-item').contains('Borrower — Return handover').should('be.visible')

    // The regression itself: the old shared "At handover" label is gone, and each
    // caption's role and moment are a single unambiguous string.
    cy.get('.conversation-evidence-item').contains('At handover').should('not.exist')
    cy.get('.conversation-evidence-item')
      .eq(1)
      .find('figcaption')
      .should('contain.text', 'Lender — Handover')
      .and('not.contain.text', 'Borrower')
    cy.get('.conversation-evidence-item')
      .eq(3)
      .find('figcaption')
      .should('contain.text', 'Borrower — Return handover')
      .and('not.contain.text', 'Lender')
  })

  it('renders a capturedAt timestamp on every evidence item', () => {
    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains(marker).closest('.transaction-card').contains('button', 'Conversation').click()

    cy.get('.conversation-evidence-item', { timeout: 15000 }).should('have.length', 4)

    // One timestamp per item, each non-empty and shaped like "Sep 12 14:03".
    cy.get('.conversation-evidence-item .conversation-evidence-time').should('have.length', 4)
    cy.get('.conversation-evidence-item .conversation-evidence-time').each(($el) => {
      expect($el.text().trim()).to.match(/^\w{3,}\s+\d{1,2}\s+\d{1,2}:\d{2}$/)
    })

    // The timestamp is inside the caption, after the role/moment text.
    cy.get('.conversation-evidence-item')
      .first()
      .find('figcaption')
      .should('contain.text', 'Lender — Pre-lending')
      .find('.conversation-evidence-time')
      .should('be.visible')
  })

  it('renders evidence in the chronological order the server returns', () => {
    // Capture the authoritative order from the API, then assert the DOM matches
    // it item for item. Comparing against the API rather than hardcoding a
    // sequence keeps this robust while still proving the UI does not reshuffle.
    let apiOrder
    loginViaApi(ahmed)
    cy.request('GET', `/api/transactions/${txnId}/evidence`).then((res) => {
      apiOrder = res.body.map((e) => e.type)
      expect(apiOrder).to.have.length(4)
      // Slice 1 ordering contract: capturedAt never decreases, and ties fall
      // back to ascending id.
      res.body.forEach((e, i) => {
        if (i > 0) {
          expect(new Date(e.capturedAt).getTime())
            .to.be.at.least(new Date(res.body[i - 1].capturedAt).getTime())
        }
      })
    })

    const expectedLabels = {
      LENDER_PRE_LENDING: 'Lender — Pre-lending',
      LENDER_HANDOVER: 'Lender — Handover',
      BORROWER_PRE_RETURN: 'Borrower — Before return',
      BORROWER_RETURN_HANDOVER: 'Borrower — Return handover',
    }

    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains(marker).closest('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-evidence-item', { timeout: 15000 }).should('have.length', 4)

    // Top to bottom, the captions follow the server's sequence exactly.
    cy.get('.conversation-evidence-item figcaption').then(($captions) => {
      const rendered = [...$captions].map((el) => el.textContent)
      expect(rendered).to.have.length(4)
      apiOrder.forEach((type, i) => {
        expect(rendered[i]).to.contain(expectedLabels[type])
      })
    })
  })

  it('keeps condition note and rating visible alongside the new metadata', () => {
    loginViaUi(ahmed)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains(marker).closest('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-evidence-item', { timeout: 15000 }).should('have.length', 4)

    // V2.5.1 metadata is unchanged, now followed by the timestamp.
    cy.get('.conversation-evidence-item').contains('Pre-lending: frame intact · 3/5').should('be.visible')
    cy.get('.conversation-evidence-item').contains('Handover: handed in good order · 4/5').should('be.visible')

    // Metadata and timestamp coexist in the same caption, so the longer caption
    // must wrap rather than be clipped to a single line.
    cy.get('.conversation-evidence-item')
      .eq(0)
      .find('figcaption')
      .should('contain.text', 'Pre-lending: frame intact · 3/5')
      .find('.conversation-evidence-time')
      .should('be.visible')
  })
})
