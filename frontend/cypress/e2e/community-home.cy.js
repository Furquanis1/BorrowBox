describe('Community Home · Recently offered (Slice 1)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }

  let cseId

  const listingFixtures = (count) =>
    Array.from({ length: count }, (_, i) => ({
      id: 300000 + i,
      assetId: 400000 + i,
      title: `Recently offered item ${i + 1}`,
      description: `Description ${i + 1}`,
      availableUnits: 1,
      borrowedUnits: 0,
      totalUnits: 1,
      waitingCount: 0,
    }))

  const signInAs = (email, password) => {
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').clear().type(email)
    cy.get('input[type="password"]').clear().type(password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/communities/', { timeout: 15000 })
  }

  before(() => {
    cy.request('POST', '/api/auth/login', ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
    })
  })

  beforeEach(() => {
    // The error/recovery test deliberately injects a failing first request; the
    // app still renders its error state, but the initial fetch rejection
    // surfaces as an uncaught promise rejection (useAsync sets state and
    // rethrows). Tolerate exactly the injected message; any other uncaught
    // error still fails the test.
    cy.on('uncaught:exception', (error) => {
      return !String(error?.message || '').includes('Temporary outage')
    })
    cy.clearCookies()
    cy.clearLocalStorage()
    signInAs(salah.email, salah.password)
  })

  it('shows the Recently offered section with real community listings', () => {
    cy.visit(`/communities/${cseId}`)

    cy.get('#recently-offered-title').should('contain', 'Recently offered')
    cy.get('.community-home .explore-listing-row').should('have.length.at.least', 1)
    cy.get('.community-home .explore-listing-row')
      .first()
      .should('contain', 'available')
  })

  it('caps the Recently offered rows at six items', () => {
    cy.intercept('GET', `/api/communities/${cseId}/listings`, {
      body: listingFixtures(8),
    })

    cy.visit(`/communities/${cseId}`)

    cy.get('.community-home .explore-listing-row').should('have.length', 6)
    cy.get('.community-home').should('not.contain', 'Recently offered item 7')
    cy.get('.community-home').should('not.contain', 'Recently offered item 8')
  })

  it('shows the empty state when nothing has been shared', () => {
    cy.intercept('GET', `/api/communities/${cseId}/listings`, { body: [] })

    cy.visit(`/communities/${cseId}`)

    cy.get('#recently-offered-title').should('contain', 'Recently offered')
    cy.get('.empty-state-title').should('have.text', 'Nothing shared yet')
    cy.get('.empty-state-description').should(
      'have.text',
      'Items offered in this community will show up here.',
    )
  })

  it('shows the error state and recovers via Try again', () => {
    let failing = true
    cy.intercept('GET', `/api/communities/${cseId}/listings`, (req) => {
      if (failing) {
        failing = false
        req.reply(500, { message: 'Temporary outage' })
      } else {
        req.reply(200, listingFixtures(4))
      }
    })

    cy.visit(`/communities/${cseId}`)

    cy.get('.empty-state-title').should('have.text', 'Could not load listings')
    cy.get('.empty-state').contains('button', 'Try again').click()

    cy.get('.community-home .explore-listing-row').should('have.length', 4)
    cy.get('#recently-offered-title').should('contain', 'Recently offered')
    cy.get('.empty-state').should('not.exist')
  })

  it('renders listings as read-only with no request or waitlist actions', () => {
    cy.intercept('GET', `/api/communities/${cseId}/listings`, {
      body: listingFixtures(2),
    })

    cy.visit(`/communities/${cseId}`)

    cy.get('.community-home .explore-listing-row').should('have.length', 2)
    cy.get('.community-home .explore-listing-row button').should('not.exist')
    cy.get('.community-home').should('not.contain', 'Request')
    cy.get('.community-home').should('not.contain', 'Join waitlist')
  })
})