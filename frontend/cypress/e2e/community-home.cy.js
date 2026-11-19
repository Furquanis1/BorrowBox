describe('Community Home · community hub (Slice 1)', () => {
  const salah = { email: 'salah@example.com', password: Cypress.env('seedPassword') }

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

  const expectNoItemActions = () => {
    cy.get('.community-home .explore-listing-row').should('not.exist')
    cy.get('.community-home .tb-card').should('not.exist')
    cy.get('.community-home .community-browse-controls').should('not.exist')
    cy.get('.community-home input[type="search"]').should('not.exist')
    cy.get('.community-home').should('not.contain', 'Recently offered')
    cy.get('.community-home').should('not.contain', 'Join waitlist')
    cy.get('.community-home button').should('not.contain', 'Request')
    cy.get('.community-home button').should('not.contain', 'Join waitlist')
  }

  before(() => {
    cy.request('POST', '/api/auth/login', {
      email: 'ahmed@example.com',
      password: Cypress.env('seedPassword'),
    })
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
    })
  })

  beforeEach(() => {
    cy.clearCookies()
    cy.clearLocalStorage()
    signInAs(salah.email, salah.password)
  })

  it('renders the community hub with pulse metrics, guidelines, and quick actions', () => {
    cy.visit(`/communities/${cseId}`)

    cy.get('.community-home .community-hub').should('be.visible')
    cy.get('.community-home-section-title').should('contain', 'Community Pulse')
    cy.get('.community-pulse-item').should('have.length', 4)
    cy.get('.community-pulse').should('contain', 'Active Members')
    cy.get('.community-pulse').should('contain', 'Available Items')
    cy.get('.community-pulse').should('contain', 'Active Rules')
    cy.get('.community-pulse').should('contain', 'Admission Mode')
    cy.get('#community-guidelines-title').should('contain', 'Community Guidelines')
    cy.get('#community-actions-title').should('contain', 'Quick Community Actions')
    expectNoItemActions()
  })

  it('shows the Explore CTA instead of item cards when listings are available', () => {
    cy.intercept('GET', `/api/communities/${cseId}/listings`, {
      body: listingFixtures(8),
    })

    cy.visit(`/communities/${cseId}`)

    cy.get('.community-explore-card').should('be.visible')
    cy.get('.community-explore-cta')
      .should('be.visible')
      .and('have.text', 'Explore Borrowable Items →')
    cy.get('.community-home').should('not.contain', 'Recently offered item 1')
    expectNoItemActions()
  })

  it('keeps the hub useful when nothing has been shared', () => {
    cy.intercept('GET', `/api/communities/${cseId}/listings`, { body: [] })

    cy.visit(`/communities/${cseId}`)

    cy.get('.community-hub').should('be.visible')
    cy.get('.community-pulse').should('contain', 'Available Items')
    cy.get('.community-explore-cta').should('be.visible')
    cy.get('.community-home .empty-state').should('not.exist')
    expectNoItemActions()
  })

  it('does not surface listing errors on the hub', () => {
    cy.intercept('GET', `/api/communities/${cseId}/listings`, {
      forceNetworkError: true,
    })

    cy.visit(`/communities/${cseId}`)

    cy.get('.community-hub').should('be.visible')
    cy.get('.community-explore-cta').should('be.visible')
    cy.get('.community-home').should('not.contain', 'Could not load listings')
    cy.get('.community-home .empty-state').should('not.exist')
    expectNoItemActions()
  })

  it('navigates from the hub CTA to Explore', () => {
    cy.visit(`/communities/${cseId}`)

    cy.get('.community-explore-cta').click()
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/explore`)
    cy.get('.community-home').should('not.exist')
  })
})
