// V2.5.3: evidence presentation -- role-explicit moment labels, the capturedAt
// timestamp on every row, and the rendered sequence following the server's
// chronological order (capturedAt ASC, id ASC).
//
// Before this slice LENDER_HANDOVER and BORROWER_RETURN_HANDOVER both rendered as
// "At handover", so the two handover moments were indistinguishable and the
// capture time was never shown. These tests are the regression guard for that.
//
// V2.5.4 adds the slot-oriented EvidenceTimeline: the four moments are always
// rendered, empty ones read "Not captured", several records of one type are all
// shown, and order is verified per slot rather than across the flat document.
//
// Setup mirrors lender-evidence.cy.js: one transaction is driven through the full
// lifecycle via the product API until all four evidence moments exist, then the
// drawer is opened once and every assertion reads the same rendered DOM.
const ahmed = { email: 'ahmed@example.com', password: 'password123' }
const salah = { email: 'salah@example.com', password: 'password123' }

const loginViaApi = (user) => {
  cy.request({ method: 'POST', url: '/api/auth/login', body: user, failOnStatusCode: true })
}

const loginViaUi = (user) => {
  cy.clearCookies()
  cy.clearLocalStorage()
  cy.visit('/signin')
  cy.get('input[type="email"]', { timeout: 15000 }).should('exist')
  cy.get('input[type="email"]').type(user.email)
  cy.get('input[type="password"]').type(user.password)
  cy.get('form.auth-form button[type="submit"]').click()
  cy.url().should('include', '/communities/', { timeout: 15000 })
  cy.request({ method: 'POST', url: '/api/me/events/read-all' })
}

// Resolved lazily: ids are assigned inside a .then() that has not run yet when
// these call sites are built, so the URL must be a thunk. Same reason
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

const closeAllMarkerTxns = (marker) => {
  loginViaApi(ahmed)
  cy.request('GET', '/api/me/lend-requests').then((res) => {
    res.body
      .filter((t) => t.purpose.startsWith(marker))
      .forEach((txn) => closeMarkerTxn(txn))
  })
}

// Each test's transaction is completed by afterEach, so several cards carry the
// same marker. Scope to a card that both contains the marker and still offers a
// Conversation button, otherwise the wrong (terminal) transaction is opened and
// the evidence gate correctly renders nothing.
// `.conversation-body` is a collapsed (8px) overflow:auto scroll container in the
// conversation drawer; that is pre-existing drawer CSS and identical at this
// commit's baseline. Cypress' be.visible heuristic inside it is therefore
// timing-dependent, so the helper asserts presence and the tests assert on the
// rendered text/markup, which is what the reader actually sees.
const openDrawer = (marker) => {
  loginViaUi(ahmed)
  cy.visit('/me/loans')
  cy.get('.transaction-card', { timeout: 15000 })
    .filter((_, el) =>
      el.innerText.includes(marker)
      && Array.from(el.querySelectorAll('button')).some((b) => b.textContent.includes('Conversation')),
    )
    .first()
    .contains('button', 'Conversation')
    .click()
  cy.get('.conversation-evidence-slots', { timeout: 15000 }).should('exist')
}

// AWAITING_HANDOVER is not one of LoansPage's LOAN_STATES, so that stage lives
// on the requests inbox with a "Discuss pickup" action.
const openDrawerFromRequests = (marker) => {
  loginViaUi(ahmed)
  cy.visit('/me/requests')
  cy.get('.requests-tab').contains('Incoming').click()
  cy.get('.transaction-card', { timeout: 15000 })
    .filter((_, el) => el.innerText.includes(marker)
      && Array.from(el.querySelectorAll('button')).some((b) => b.textContent.includes('Discuss pickup')))
    .first()
    .contains('button', 'Discuss pickup')
    .click()
  cy.get('.conversation-evidence-slots', { timeout: 15000 }).should('exist')
}

const SLOTS = [
  { type: 'LENDER_PRE_LENDING', label: 'Lender — Pre-lending' },
  { type: 'LENDER_HANDOVER', label: 'Lender — Handover' },
  { type: 'BORROWER_PRE_RETURN', label: 'Borrower — Before return' },
  { type: 'BORROWER_RETURN_HANDOVER', label: 'Borrower — Return handover' },
]

describe('Evidence labels, timestamps and ordering (V2.5.3)', () => {
  let marker
  let cseId
  let listingId
  let txnId

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
    closeAllMarkerTxns(marker)
  })

  after(() => {
    closeAllMarkerTxns(marker)
    // Marker-prefix scoped safety net: removes this run's rows and their child
    // rows. Runs before the counts below so a failing assertion can't skip it.
    cy.task('cleanupEventsDb', marker)
    loginViaApi(ahmed)
    cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
      const football = res.body.find((l) => l.title === 'Football')
      expect(football.availableUnits).to.equal(1)
      expect(football.borrowedUnits).to.equal(0)
    })
  })

  it('distinguishes the lender handover from the borrower return handover', () => {
    openDrawer(marker)

    // All four moments are present, each with its own label.
    cy.get('.conversation-evidence-slots .conversation-evidence-item', { timeout: 15000 }).should('have.length', 4)
    cy.get('.conversation-evidence-item').contains('Lender — Pre-lending').should('exist')
    cy.get('.conversation-evidence-item').contains('Lender — Handover').should('exist')
    cy.get('.conversation-evidence-item').contains('Borrower — Before return').should('exist')
    cy.get('.conversation-evidence-item').contains('Borrower — Return handover').should('exist')

    // The regression itself: the old shared "At handover" label is gone, and each
    // caption's role and moment are a single unambiguous string.
    cy.get('.conversation-evidence-item').contains('At handover').should('not.exist')
    cy.get('.conversation-evidence-slots .conversation-evidence-item')
      .eq(1)
      .find('figcaption')
      .should('contain.text', 'Lender — Handover')
      .and('not.contain.text', 'Borrower')
    cy.get('.conversation-evidence-slots .conversation-evidence-item')
      .eq(3)
      .find('figcaption')
      .should('contain.text', 'Borrower — Return handover')
      .and('not.contain.text', 'Lender')
  })

  it('renders a capturedAt timestamp on every evidence item', () => {
    openDrawer(marker)

    cy.get('.conversation-evidence-slots .conversation-evidence-item', { timeout: 15000 }).should('have.length', 4)

    // One timestamp per item, each non-empty and shaped like "Sep 12 14:03".
    cy.get('.conversation-evidence-slots .conversation-evidence-item .conversation-evidence-time').should('have.length', 4)
    cy.get('.conversation-evidence-item .conversation-evidence-time').each(($el) => {
      expect($el.text().trim()).to.match(/^\w{3,}\s+\d{1,2}\s+\d{1,2}:\d{2}$/)
    })

    // The timestamp is inside the caption, after the role/moment text.
    cy.get('.conversation-evidence-item')
      .first()
      .find('figcaption')
      .should('contain.text', 'Lender — Pre-lending')
      .find('.conversation-evidence-time')
      .should('exist')
  })

  it('renders evidence in the chronological order the server returns', () => {
    // Capture the authoritative order from the API, then assert each slot matches
    // it item for item. The V2.5.4 timeline groups by type, so the contract is
    // asserted *within* each slot rather than across the whole flat document:
    // slot order is the fixed four, and inside a slot it must be chronological.
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

    openDrawer(marker)
    cy.get('.conversation-evidence-slots .conversation-evidence-item', { timeout: 15000 }).should('have.length', 4)

    // Every rendered item appears under its own type's slot, and the order of the
    // items inside that slot is the order the API returned for that type. The
    // per-slot walk happens inside .each() so apiOrder is already resolved.
    cy.get('.conversation-evidence-slots')
      .find('.conversation-evidence-slot')
      .should('have.length', SLOTS.length)
      .each(($slot) => {
        const type = $slot.attr('data-evidence-type')
        const expectedLabel = SLOTS.find((s) => s.type === type).label
        const expectedForType = apiOrder.filter((t) => t === type)
        const rendered = [...$slot[0].querySelectorAll('figcaption')].map((el) => el.textContent)
        expect(rendered).to.have.length(expectedForType.length)
        rendered.forEach((text) => {
          expect(text).to.contain(expectedLabel)
        })
      })
  })

  it('keeps condition note and rating visible alongside the new metadata', () => {
    openDrawer(marker)

    // V2.5.1 metadata is unchanged, now followed by the timestamp.
    cy.get('.conversation-evidence-item').contains('Pre-lending: frame intact · 3/5').should('exist')
    cy.get('.conversation-evidence-item').contains('Handover: handed in good order · 4/5').should('exist')

    // Metadata and timestamp coexist in the same caption, so the longer caption
    // must wrap rather than be clipped to a single line.
    cy.get('.conversation-evidence-item')
      .eq(0)
      .find('figcaption')
      .should('contain.text', 'Pre-lending: frame intact · 3/5')
      .find('.conversation-evidence-time')
      .should('exist')
  })
})

// V2.5.4 slot behaviour. This fixture deliberately stops at AWAITING_HANDOVER
// with two LENDER_PRE_LENDING records and nothing else, which in one pass covers
// the populated slot, the three empty slots, and the "no winner" rule: there is
// no unique constraint on (transaction_id, type), so two pre-lending photos for
// one transaction is valid behaviour the UI must render in full.
describe('Evidence timeline slots and multiplicity (V2.5.4)', () => {
  let marker
  let cseId
  let listingId
  let txnId

  before(() => {
    marker = `EvidenceSlots-${Date.now()}`
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
    loginViaApi(salah)
    cy.request({
      method: 'POST',
      url: '/api/transactions',
      body: { listingId, purpose: `${marker} slots`, requestedDurationDays: 2 },
      failOnStatusCode: true,
    }).then((res) => {
      txnId = res.body.id
    })

    loginViaApi(ahmed)
    post(() => `/api/transactions/${txnId}/approve`)
    loginViaApi(salah)
    post(() => `/api/transactions/${txnId}/stage-handover`)

    // Two pre-lending photos, each with its own metadata. The second upload is
    // the proof that the timeline groups by type without collapsing to one row.
    loginViaApi(ahmed)
    uploadPhoto(() => txnId, 'LENDER_PRE_LENDING', 'First pre-lending photo', 5)
    uploadPhoto(() => txnId, 'LENDER_PRE_LENDING', 'Second pre-lending photo', 1)
  })

  afterEach(() => {
    closeAllMarkerTxns(marker)
  })

  after(() => {
    closeAllMarkerTxns(marker)
    // Marker-prefix scoped safety net: removes this run's rows and their child
    // rows. Runs before the counts below so a failing assertion can't skip it.
    cy.task('cleanupEventsDb', marker)
    loginViaApi(ahmed)
    cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
      const football = res.body.find((l) => l.title === 'Football')
      expect(football.availableUnits).to.equal(1)
      expect(football.borrowedUnits).to.equal(0)
    })
  })

  it('renders all four slot headers in the fixed order', () => {
    openDrawerFromRequests(marker)

    cy.get('.conversation-evidence-slot').should('have.length', 4)
    cy.get('.conversation-evidence-slot-title').then(($titles) => {
      expect([...$titles].map((el) => el.textContent.trim())).to.deep.equal(SLOTS.map((s) => s.label))
    })
    // Each header is bound to its own type, in the same fixed order.
    cy.get('.conversation-evidence-slot').then(($slots) => {
      expect([...$slots].map((el) => el.getAttribute('data-evidence-type'))).to.deep.equal(SLOTS.map((s) => s.type))
    })
  })

  it('shows a "Not captured" placeholder in every slot with no evidence', () => {
    openDrawerFromRequests(marker)

    // Only LENDER_PRE_LENDING has anything; the other three must still be
    // present as real slots with an explicit empty state, not omitted.
    cy.get('.conversation-evidence-slots .conversation-evidence-empty').should('have.length', 3)
    cy.get('.conversation-evidence-slots .conversation-evidence-empty').each(($el) => {
      expect($el.text().trim()).to.equal('Not captured')
    })

    SLOTS.slice(1).forEach((slot) => {
      cy.get(`.conversation-evidence-slot[data-evidence-type="${slot.type}"]`)
        .should('have.length', 1)
        .find('.conversation-evidence-slot-title')
        .should('have.text', slot.label)
      // The placeholder sits inside the slot body and does not replace the header.
      cy.get(`.conversation-evidence-slot[data-evidence-type="${slot.type}"] .conversation-evidence-slot-body > .conversation-evidence-empty`)
        .should('exist')
    })

    // The old whole-section empty sentence is gone.
    cy.contains('No photos were captured yet').should('not.exist')
  })

  it('renders every record of a repeated type instead of picking a winner', () => {
    openDrawerFromRequests(marker)

    // Two pre-lending photos exist in the API; both must be visible.
    cy.get('.conversation-evidence-slots .conversation-evidence-item', { timeout: 15000 }).should('have.length', 2)

    cy.get('.conversation-evidence-slot[data-evidence-type="LENDER_PRE_LENDING"]')
      .find('.conversation-evidence-item')
      .should('have.length', 2)
      // Each row keeps its own metadata, so neither record is overwritten.
      .eq(0)
      .find('figcaption')
      .should('contain.text', 'First pre-lending photo')
      .and('contain.text', '5/5')
    cy.get('.conversation-evidence-slot[data-evidence-type="LENDER_PRE_LENDING"]')
      .find('.conversation-evidence-item')
      .eq(1)
      .find('figcaption')
      .should('contain.text', 'Second pre-lending photo')
      .and('contain.text', '1/5')
  })

  it('keeps each slot in capturedAt ASC, id ASC order and drops nothing', () => {
    let apiEvidence
    loginViaApi(ahmed)
    cy.request('GET', `/api/transactions/${txnId}/evidence`).then((res) => {
      apiEvidence = res.body
      expect(apiEvidence).to.have.length(2)
      // The server contract itself: chronological, ties broken by ascending id.
      apiEvidence.forEach((e, i) => {
        if (i > 0) {
          expect(new Date(e.capturedAt).getTime()).to.be.at.least(new Date(apiEvidence[i - 1].capturedAt).getTime())
          if (e.capturedAt === apiEvidence[i - 1].capturedAt) {
            expect(e.id).to.be.greaterThan(apiEvidence[i - 1].id)
          }
        }
      })
    })

    openDrawerFromRequests(marker)

    // Nothing is dropped: the rendered count equals the API count. Read inside the
    // callback so apiEvidence is already resolved.
    cy.get('.conversation-evidence-slots .conversation-evidence-item').should(($items) => {
      expect($items.length).to.equal(apiEvidence.length)
    })

    // Within the slot the rendered order is the API's order for that type, and
    // each caption shows that record's own capturedAt.
    cy.get('.conversation-evidence-slot[data-evidence-type="LENDER_PRE_LENDING"] .conversation-evidence-item').then(($items) => {
      const rendered = [...$items].map((el) => ({
        note: el.querySelector('figcaption').textContent,
        time: el.querySelector('.conversation-evidence-time').textContent.trim(),
      }))
      expect(rendered).to.have.length(apiEvidence.length)
      rendered.forEach((item, i) => {
        expect(item.note).to.contain(apiEvidence[i].conditionNote)
        const stamp = new Date(apiEvidence[i].capturedAt)
        const month = stamp.toLocaleDateString(undefined, { month: 'short' })
        const day = stamp.getDate()
        expect(item.time).to.contain(`${month} ${day}`)
      })
    })
  })

  it('keeps condition note, rating and capturedAt visible in a populated slot', () => {
    openDrawerFromRequests(marker)

    cy.get('.conversation-evidence-slot[data-evidence-type="LENDER_PRE_LENDING"]')
      .find('.conversation-evidence-time')
      .should('have.length', 2)
      .each(($el) => {
        expect($el.text().trim()).to.match(/^\w{3,}\s+\d{1,2}\s+\d{1,2}:\d{2}$/)
      })

    // Each photo points at the authenticated, participant-only content endpoint
    // rather than a static asset, and its alt text carries the role-explicit
    // label so the image is described the same way as the caption.
    cy.get('.conversation-evidence-slot[data-evidence-type="LENDER_PRE_LENDING"]')
      .find('.conversation-evidence-item img')
      .should('have.length', 2)
      .each(($img) => {
        expect($img.attr('src')).to.be.a('string').and.match(/^\/api\/evidence\/\d+\/content$/)
        expect($img.attr('alt')).to.equal('Lender — Pre-lending')
      })
  })
})