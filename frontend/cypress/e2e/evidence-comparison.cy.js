// V2.5.5: Before/After comparison coverage.
//
// Drives real marker transactions through the real lifecycle and asserts the
// rendered `.conversation-comparison` in the conversation drawer, which is the
// only place this view exists. Nothing here reaches into component internals:
// the assertions use the visible side headings, the caption text the drawer
// actually shows, and the participant-only evidence API as the source of truth.
//
// The point of the view is that it maps two fixed product positions and draws
// no conclusion: BEFORE is the lender's pre-lending moment, AFTER is the
// borrower's pre-return moment. So these tests deliberately assert *mapping,
// completeness, per-side order and framing*, and deliberately never assert a
// verdict, a winner, a "better" photo, or a chronological order across the two
// sides (the two sides are different moments; their relative order is not a
// claim this view makes).
//
// Canonical fixture: Football (Ahmed) 2 units, 1 AVAILABLE + 1 RESERVED. Every
// transaction here carries a unique purpose marker, and the `after` hook closes
// it through the product API before falling back to the marker-scoped
// `cleanupEventsDb` task, so no marker row or child row survives the run —
// including when a test fails part way through.
describe('Before/After comparison (V2.5.5)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }
  const omar = { email: 'omar@example.com', password: 'password123' }

  let marker
  let cseId
  let listingId
  let txnId

  // V2.5.5 side subtitles, verbatim from the component's COMPARISON_SIDES.
  const BEFORE_MOMENT = 'Lender — Pre-lending'
  const AFTER_MOMENT = 'Borrower — Before return'
  const DISCLAIMER = 'Photos are shown as captured. They are not an automatic condition assessment.'

  // Substring, case-insensitive. Deliberately the stems rather than whole
  // words: "damage" must also catch "damaged", "differ" must also catch
  // "differs"/"different", and "changed" must also catch "changed condition".
  // None of these strings appear in the seeded fixture text this spec uploads
  // (see the condition notes below), so a hit means the view inferred something.
  const FORBIDDEN_VERDICT_WORDS = [
    'damage',
    'mismatch',
    'worse',
    'better',
    'identical',
    'same condition',
    'differ',
    'changed',
  ]

  /** Asserts the scoped section states no verdict, by substring, case-insensitively. */
  const assertNoVerdictLanguage = (scope) => {
    cy.get(scope).invoke('text').then((raw) => {
      const text = String(raw).toLowerCase()
      FORBIDDEN_VERDICT_WORDS.forEach((term) => {
        expect(text, `${scope} must not contain the verdict word "${term}"`).to.not.include(term)
      })
    })
  }

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

  const resolveUrl = (build) => (typeof build === 'function' ? build() : build)

  // Every URL that interpolates `txnId` must go through these thunks. A bare
  // `cy.request('GET', `/api/transactions/${txnId}/...`)` builds its string while
  // the test body is still queuing commands, i.e. before the POST /transactions
  // that assigns txnId has resolved -- which silently sends /transactions/undefined
  // and makes the assertions pass against nothing.
  const post = (build) => cy.wrap(null).then(() => cy.request({ method: 'POST', url: resolveUrl(build) }))

  const get = (build) => cy.wrap(null).then(() => cy.request({ method: 'GET', url: resolveUrl(build) }))

  const getExpecting = (build) =>
    cy.wrap(null).then(() => cy.request({ method: 'GET', url: resolveUrl(build), failOnStatusCode: false }))

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
    marker = `Comparison-${Date.now()}`
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

  // No afterEach: these tests are ordered stages of one real loan, so the
  // transaction has to survive from the first `it` to the last. `after` still
  // runs when any of them fails, which is what keeps a failed run from leaking.
  after(() => {
    loginViaApi(ahmed)
    cy.request('GET', '/api/me/lend-requests').then((res) => {
      res.body
        .filter((t) => t.purpose.startsWith(marker))
        .forEach((txn) => closeMarkerTxn(txn))
    })
    // Marker-prefix scoped: removes only this run's rows and their child rows.
    cy.task('cleanupEventsDb', marker)
    footballCounts().then(({ available, borrowed }) => {
      expect(available).to.equal(1)
      expect(borrowed).to.equal(0)
    })
  })

  it('AWAITING_HANDOVER: Before maps every lender pre-lending photo and After reads Not captured', () => {
    // ── A real request, approved and staged for handover ───────────────
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} Before/after comparison`, requestedDurationDays: 2 },
    }).then((res) => {
      txnId = res.body.id
    })
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)

    // ── Deliberately TWO pre-lending photos, to prove nothing is picked ──
    loginViaApi(ahmed)
    uploadPhoto(() => txnId, 'LENDER_PRE_LENDING', 'Pre-lending snapshot A', 3)
    uploadPhoto(() => txnId, 'LENDER_PRE_LENDING', 'Pre-lending snapshot B', 4)

    // ── Open the real drawer as the lender ─────────────────────────────
    loginViaUi(ahmed)
    cy.visit('/me/requests')
    cy.get('.requests-tab').contains('Incoming').click()
    cy.get('.transaction-card').contains(marker).closest('.transaction-card').contains('button', 'Discuss pickup').click()
    cy.get('.conversation-body', { timeout: 15000 }).should('be.visible')

    // The four always-present timeline slots plus the comparison put this
    // section below the fold of the drawer's own scroll container, exactly as
    // in lender-evidence.cy.js. Scroll the section itself into view once, then
    // assert visibility inside it normally.
    cy.get('.conversation-comparison').scrollIntoView().should('be.visible')

    // ── 1. Both sides always render ───────────────────────────────────
    cy.get('.conversation-comparison [data-comparison-side="before"]').should('be.visible')
    cy.get('.conversation-comparison [data-comparison-side="after"]').should('be.visible')

    // ── The locked mapping: which moment each side stands for ──────────
    cy.get('[data-comparison-side="before"] .conversation-comparison-side-title')
      .should('contain.text', 'Before')
      .and('contain.text', BEFORE_MOMENT)
    cy.get('[data-comparison-side="after"] .conversation-comparison-side-title')
      .should('contain.text', 'After')
      .and('contain.text', AFTER_MOMENT)

    // ── 7. Neutral framing, stated exactly once ────────────────────────
    cy.get('.conversation-comparison-note').should('have.length', 1).and('have.text', DISCLAIMER)

    // ── 8. No inferred verdict anywhere in the comparison ──────────────
    assertNoVerdictLanguage('.conversation-comparison')

    // API read inside .then(), never handed to should() as a pre-read array.
    get(() => `/api/transactions/${txnId}/evidence`).then((res) => {
      const expectedBefore = res.body.filter((e) => e.type === 'LENDER_PRE_LENDING')

      // ── 2 + 4 + 11. Both records present, neither dropped, count = API ─
      expect(expectedBefore.length, 'fixture must really hold two pre-lending photos').to.equal(2)
      cy.get('[data-comparison-side="before"] .conversation-evidence-item')
        .should('have.length', expectedBefore.length)

      // ── 14. Each card is the right source record ─────────────────────
      expectedBefore.forEach((expected, i) => {
        cy.get('[data-comparison-side="before"] .conversation-evidence-item')
          .eq(i)
          .within(() => {
            // alt/contentUrl rather than image bytes: the fixture is the
            // repo's tiny non-decodable PNG, so there is nothing to decode.
            cy.get('img').should('have.attr', 'alt', BEFORE_MOMENT)
            cy.get('img').should('have.attr', 'src', expected.contentUrl)
            cy.get('figcaption').should('contain.text', `${BEFORE_MOMENT} · ${expected.capturerName}`)
            cy.get('figcaption').should('contain.text', expected.conditionNote)
            cy.get('figcaption').should('contain.text', `${expected.conditionRating}/5`)
            cy.get('figcaption .conversation-evidence-time').should('exist')
          })
      })

      // ── 5. Incoming API chronology preserved inside Before ────────────
      cy.get('[data-comparison-side="before"] .conversation-evidence-item figcaption').each(($fig, i) => {
        expect($fig.text()).to.include(expectedBefore[i].conditionNote)
      })
    })

    // ── 6. Missing side still renders, saying exactly "Not captured" ───
    cy.get('[data-comparison-side="after"] .conversation-evidence-item').should('not.exist')
    cy.get('[data-comparison-side="after"] .conversation-evidence-empty').should('have.text', 'Not captured')

    cy.get('button[aria-label="Close"]').click()
  })

  it('keeps the evidence behind participant-only authorization', () => {
    // A non-participant can reach neither the transaction nor the evidence
    // that feeds the comparison, so there is nothing for them to render.
    loginViaApi(omar)
    getExpecting(() => `/api/transactions/${txnId}/evidence`).then((res) => {
      expect(res.status).to.equal(401)
    })
    getExpecting(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.status).to.equal(401)
    })

    // Both actual participants still read it.
    loginViaApi(ahmed)
    get(() => `/api/transactions/${txnId}/evidence`).its('status').should('equal', 200)
    loginViaApi(salah)
    get(() => `/api/transactions/${txnId}/evidence`).its('status').should('equal', 200)
  })

  it('RETURN_INITIATED: Before keeps the lender photos while After gains the borrower pre-return photos', () => {
    // ── Move the same loan on through the real lifecycle ───────────────
    loginViaApi(ahmed)
    uploadPhoto(() => txnId, 'LENDER_HANDOVER', 'Handover snapshot', 4)
    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/confirm-handover`)
    get(() => `/api/transactions/${txnId}`).then((res) => {
      expect(res.body.state).to.equal('ACTIVE')
    })

    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/initiate-return`)

    // Two pre-return photos, for the same completeness reason as Before.
    loginViaApi(salah)
    uploadPhoto(() => txnId, 'BORROWER_PRE_RETURN', 'Pre-return snapshot A', 5)
    uploadPhoto(() => txnId, 'BORROWER_PRE_RETURN', 'Pre-return snapshot B', 5)

    // ── Open the real drawer as the borrower ───────────────────────────
    loginViaUi(salah)
    cy.visit('/me/loans')
    cy.get('.transaction-card', { timeout: 15000 }).contains('Return in progress').should('be.visible')
    cy.get('.transaction-card').contains(marker).closest('.transaction-card').contains('button', 'Conversation').click()
    cy.get('.conversation-body', { timeout: 15000 }).should('be.visible')
    cy.get('.conversation-comparison').scrollIntoView().should('be.visible')

    get(() => `/api/transactions/${txnId}/evidence`).then((res) => {
      const expectedBefore = res.body.filter((e) => e.type === 'LENDER_PRE_LENDING')
      const expectedAfter = res.body.filter((e) => e.type === 'BORROWER_PRE_RETURN')

      // ── 2. Before is unchanged: still only the lender's pre-lending ──
      expect(expectedBefore.length).to.equal(2)
      cy.get('[data-comparison-side="before"] .conversation-evidence-item')
        .should('have.length', expectedBefore.length)
      cy.get('[data-comparison-side="before"]').should('contain.text', 'Ahmed')
      cy.get('[data-comparison-side="before"]').should('not.contain.text', 'Pre-return snapshot')

      // ── 3 + 4 + 11. After renders every pre-return record ────────────
      expect(expectedAfter.length).to.equal(2)
      cy.get('[data-comparison-side="after"] .conversation-evidence-item')
        .should('have.length', expectedAfter.length)
      cy.get('[data-comparison-side="after"] .conversation-evidence-empty').should('not.exist')

      expectedAfter.forEach((expected, i) => {
        cy.get('[data-comparison-side="after"] .conversation-evidence-item')
          .eq(i)
          .within(() => {
            cy.get('img').should('have.attr', 'alt', AFTER_MOMENT)
            cy.get('img').should('have.attr', 'src', expected.contentUrl)
            cy.get('figcaption').should('contain.text', `${AFTER_MOMENT} · ${expected.capturerName}`)
            cy.get('figcaption').should('contain.text', expected.conditionNote)
            cy.get('figcaption').should('contain.text', `${expected.conditionRating}/5`)
          })
      })

      // ── 5. Chronology asserted per side, independently ───────────────
      cy.get('[data-comparison-side="before"] .conversation-evidence-item figcaption').each(($fig, i) => {
        expect($fig.text()).to.include(expectedBefore[i].conditionNote)
      })
      cy.get('[data-comparison-side="after"] .conversation-evidence-item figcaption').each(($fig, i) => {
        expect($fig.text()).to.include(expectedAfter[i].conditionNote)
      })

      // Note: there is deliberately no assertion about the order of Before
      // relative to After. The two sides are two different moments, and this
      // view makes no claim about which came first in any global sense.
    })

    // ── 7 + 8. Framing and neutrality hold once both sides have content ─
    cy.get('.conversation-comparison-note').should('have.length', 1).and('have.text', DISCLAIMER)
    assertNoVerdictLanguage('.conversation-comparison')

    cy.get('button[aria-label="Close"]').click()
  })
})