/**
 * V2.3.1 Derived Trust Profile + Ledger Timeline
 *
 * Uses the two deterministic completed seed fixtures:
 *   - Karim borrowed Ahmed's Football in Engineering Office and returned early
 *     (on-time).
 *   - Omar borrowed Youssef's Cordless Drill in Hostel Block B and returned late.
 *   - Ahmed lent the Football to Karim and has no completed loan of his own
 *     (null on-time rate).
 *
 * The ledger timeline is exercised with a self-contained marker transaction
 * (created and approved in `before`) rather than the seeded events, because the
 * `events.cy.js` spec purges all transaction_events rows before this spec runs
 * in the full-suite order.
 */
describe('V2.3.1 Derived Trust Profile', () => {
  const password = 'password123'

  let cseId
  let listingId
  let marker
  let timelineTxnId

  const signIn = (email) => {
    cy.clearCookies()
    cy.clearLocalStorage()
    cy.request({ method: 'POST', url: '/api/auth/login', body: { email, password }, failOnStatusCode: true })
    cy.request('POST', '/api/me/events/read-all')
  }

  const stat = (label) => cy.contains('.trust-stat', label).find('.trust-stat-value')

  before(() => {
    cy.request({ method: 'POST', url: '/api/auth/login', body: { email: 'ahmed@example.com', password } })
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
    })
    cy.then(() =>
      cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
        listingId = res.body.find((l) => l.title === 'Football').id
      })
    )
    cy.then(() => {
      marker = `Profile-${Date.now()}`
      cy.request({ method: 'POST', url: '/api/auth/login', body: { email: 'salah@example.com', password } })
      cy.request({
        method: 'POST',
        url: '/api/transactions',
        body: { listingId, purpose: `${marker} Timeline fixture`, requestedDurationDays: 1 },
      }).then((res) => {
        timelineTxnId = res.body.id
      })
    })
    cy.then(() => {
      cy.request({ method: 'POST', url: '/api/auth/login', body: { email: 'ahmed@example.com', password } })
      cy.request('POST', `/api/transactions/${timelineTxnId}/approve`)
    })
  })

  after(() => {
    cy.task('cleanupEventsDb', marker)
  })

  it('Karim\'s Engineering Office scope shows his on-time return', () => {
    signIn('karim@example.com')
    cy.visit('/me/profile')
    cy.get('.profile-scope select', { timeout: 15000 }).select('Engineering Office')

    stat('Items borrowed').should('have.text', '1')
    stat('Returned on time').should('have.text', '1')
    stat('Returned late').should('have.text', '0')
    stat('On-time return rate').should('have.text', '100%')

    cy.get('.profile-history').contains('Football').should('be.visible')
    cy.get('.profile-history').contains('Completed').should('be.visible')
    cy.get('.profile-history').should('not.contain.text', 'Handover disputed')
  })

  it('Omar\'s Hostel Block B scope shows his late return', () => {
    signIn('omar@example.com')
    cy.visit('/me/profile')
    cy.get('.profile-scope select', { timeout: 15000 }).select('Hostel Block B')

    stat('Items borrowed').should('have.text', '1')
    stat('Returned on time').should('have.text', '0')
    stat('Returned late').should('have.text', '1')
    stat('On-time return rate').should('have.text', '0%')

    cy.get('.profile-history').contains('Cordless Drill').should('be.visible')
    cy.get('.profile-history').contains('Completed').should('be.visible')
  })

  it('Ahmed\'s lender scope has no completed loans and a null rate', () => {
    signIn('ahmed@example.com')
    cy.visit('/me/profile')
    cy.get('.profile-scope select', { timeout: 15000 }).select('Engineering Office')

    stat('Items lent').should('have.text', '1')
    stat('Completed loans').should('have.text', '0')
    stat('On-time return rate').should('have.text', '\u2014')
    cy.contains('.trust-stat', 'On-time return rate').should('contain.text', 'No completed loans yet')
  })

  it('switching scope re-derives the metrics', () => {
    signIn('omar@example.com')
    cy.visit('/me/profile')
    cy.get('.profile-scope select', { timeout: 15000 }).select('Hostel Block B')
    stat('On-time return rate').should('have.text', '0%')

    cy.get('.profile-scope select').select('Engineering Office')
    stat('Items borrowed').should('have.text', '0')
    stat('On-time return rate').should('have.text', '\u2014')
    cy.get('.profile-history').should('not.contain.text', 'Cordless Drill')
  })

  it('opens the participant-only ledger timeline in the conversation drawer', () => {
    signIn('salah@example.com')
    cy.visit('/me/requests')
    cy.get('.requests-tab').contains('My requests').click()
    cy.get('.transaction-card')
      .contains(marker)
      .closest('.transaction-card')
      .contains('button', 'Discuss pickup')
      .click()

    cy.get('.drawer', { timeout: 15000 }).should('be.visible')
    cy.contains('button', 'Activity timeline').click()

    cy.get('.timeline', { timeout: 15000 }).should('be.visible')
    cy.get('.timeline-item').should('have.length.at.least', 1)
    cy.get('.timeline').contains('Request approved').should('be.visible')
  })

  it('trust-profile response exposes only the derived metric fields', () => {
    signIn('karim@example.com')
    cy.request('GET', '/api/me/trust-profile').then((res) => {
      expect(res.status).to.eq(200)
      expect(Object.keys(res.body).sort()).to.deep.equal(
        [
          'communityId',
          'communityName',
          'itemsBorrowed',
          'itemsLent',
          'successfulTransactions',
          'completedLoans',
          'onTimeReturns',
          'lateReturns',
          'onTimeReturnRate',
          'returnDisputes',
          'completedLends',
          'returnDisputesReceived',
        ].sort()
      )
    })
  })

  it('timeline response exposes only event fields (no units or evidence)', () => {
    signIn('salah@example.com')
    cy.request('GET', `/api/transactions/${timelineTxnId}/timeline`).then((timelineRes) => {
      expect(timelineRes.status).to.eq(200)
      expect(timelineRes.body.map((e) => e.eventType)).to.include('REQUEST_APPROVED')
      timelineRes.body.forEach((event) => {
        expect(Object.keys(event).sort()).to.deep.equal(
          ['id', 'transactionId', 'eventType', 'actorId', 'actorName', 'payload', 'createdAt'].sort()
        )
        expect(event.payload).to.satisfy((p) => p === null || typeof p === 'string')
      })
    })
  })

  it('a non-participant cannot read another user\'s timeline', () => {
    signIn('youssef@example.com')
    cy.request({
      method: 'GET',
      url: `/api/transactions/${timelineTxnId}/timeline`,
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.eq(401)
    })
  })

  it('a non-member community scope is forbidden', () => {
    signIn('karim@example.com')
    cy.request({
      method: 'GET',
      url: `/api/me/trust-profile?communityId=${cseId}`,
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.eq(403)
    })
  })
})
