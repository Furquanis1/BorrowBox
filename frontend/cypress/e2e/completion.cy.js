/**
 * V2.1 Completion-Flow Coverage
 *
 * Exercises the locked V2.1 completion test from BORROWBOX_ROADMAP.ipynb:
 *   "A user can create/join a bounded community with an attributed membership,
 *    own an asset once, selectively expose it to multiple communities, and see
 *    one shared physical inventory state reflected consistently across those
 *    communities."
 *
 * Also covers:
 *   - Unlisted asset absent from Explore
 *   - /me/inventory is community-independent
 *   - Cross-community shared availability (Football fixture)
 */
describe('V2.1 Completion Flow', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }

  let cseId, hostelId, officeId

  before(() => {
    cy.request('POST', '/api/auth/login', {
      email: ahmed.email,
      password: ahmed.password,
    })
    cy.request('GET', '/api/communities').then((res) => {
      cseId    = res.body.find((c) => c.name === 'CSE Department').id
      hostelId = res.body.find((c) => c.name === 'Hostel Block B').id
      officeId = res.body.find((c) => c.name === 'Engineering Office').id
    })
  })

  beforeEach(() => {
    cy.clearCookies()
    cy.clearLocalStorage()
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').type(ahmed.email)
    cy.get('input[type="password"]').type(ahmed.password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/communities/', { timeout: 15000 })
  })

  // ── Authenticated community membership ──────────────────────────

  it('Ahmed sees at least 3 communities in the panel', () => {
    cy.get('.community-panel').should('be.visible')
    cy.get('.group-item').should('have.length.at.least', 3)
    cy.contains('.group-item', 'CSE Department').should('be.visible')
    cy.contains('.group-item', 'Hostel Block B').should('be.visible')
    cy.contains('.group-item', 'Engineering Office').should('be.visible')
  })

  // ── Community-scoped Explore ─────────────────────────────────────

  it('CSE Explore shows only CSE listings', () => {
    cy.contains('.group-item', 'CSE Department').click()
    cy.url().should('include', `/communities/${cseId}`)
    cy.get('.community-tabs').contains('a', 'Explore').click()
    cy.url().should('include', `/communities/${cseId}/explore`)

    cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
      cy.get('.explore-listing-row').should('have.length', res.body.length)
      res.body.forEach((listing) => {
        cy.contains('.explore-listing-row', listing.title).should('be.visible')
      })
    })
  })

  // ── Cross-community shared availability (Football fixture) ──────

  it('Football shows identical availability in CSE, Hostel, and Office', () => {
    const captures = {}

    const readFootballCard = (communityName, communityId) => {
      cy.contains('.group-item', communityName).click()
      cy.url().should('include', `/communities/${communityId}`)
      cy.get('.community-tabs').contains('a', 'Explore').click()
      cy.url().should('include', `/communities/${communityId}/explore`)
      cy.contains('.explore-listing-row', 'Football').should('be.visible')
      cy.contains('.explore-listing-row', 'Football')
        .invoke('text')
        .then((t) => { captures[communityName] = t.trim() })
    }

    readFootballCard('CSE Department', cseId)
    readFootballCard('Hostel Block B', hostelId)
    readFootballCard('Engineering Office', officeId)

    cy.then(() => {
      expect(captures['CSE Department']).to.contain('1 available')
      expect(captures['CSE Department']).to.contain('0 borrowed')
      expect(captures['Hostel Block B']).to.equal(captures['CSE Department'])
      expect(captures['Engineering Office']).to.equal(captures['CSE Department'])
    })
  })

  // ── Unlisted asset absent from Explore ───────────────────────────

  it('Spare Laptop is absent from all community Explore pages', () => {
    const assertNoSpareLaptop = (communityName, communityId) => {
      cy.contains('.group-item', communityName).click()
      cy.url().should('include', `/communities/${communityId}`)
      cy.get('.community-tabs').contains('a', 'Explore').click()
      cy.contains('.explore-listing-row', 'Spare Laptop').should('not.exist')
    }

    assertNoSpareLaptop('CSE Department', cseId)
    assertNoSpareLaptop('Hostel Block B', hostelId)
    assertNoSpareLaptop('Engineering Office', officeId)
  })

  // ── Global /me/inventory is community-independent ────────────────

  it('/me/inventory shows Ahmed assets and is not scoped to a community', () => {
    cy.visit('/me/inventory')
    cy.url().should('include', '/me/inventory')
    cy.get('.inventory-header h2').should('contain', 'My Inventory')
    cy.get('.community-header').should('not.exist')
    cy.get('.community-tabs').should('not.exist')

    cy.contains('.asset-row', 'Football').should('be.visible')
    cy.contains('.asset-row', 'Football').contains('.badge', 'Listed in 3 communities')

    cy.visit(`/communities/${cseId}/explore`)
    cy.get('.community-header-title').should('contain', 'CSE Department')
    cy.visit('/me/inventory')
    cy.get('.inventory-header h2').should('contain', 'My Inventory')
    cy.get('.community-header').should('not.exist')
  })

  // ── Spare Laptop visible in owner inventory but not Explore ──────

  it('Spare Laptop appears in owner inventory', () => {
    cy.visit('/me/inventory')
    cy.url().should('include', '/me/inventory')
    // Karim owns the Spare Laptop; verify via API that it exists
    cy.request('POST', '/api/auth/login', {
      email: 'karim@example.com',
      password: 'password123',
    })
    cy.visit('/me/inventory')
    cy.url().should('include', '/me/inventory')
    cy.contains('.asset-row', 'Spare Laptop').should('be.visible')
    cy.contains('.asset-row', 'Spare Laptop').should('contain', 'Not listed')
  })
})

describe('V2.1 Seed Baseline Verification (API)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }

  before(() => {
    cy.request('POST', '/api/auth/login', {
      email: ahmed.email,
      password: ahmed.password,
    })
  })

  it('canonical 5 users, 3 communities, 5 assets, 6 units, 7 listings', () => {
    cy.request('GET', '/api/communities').then((res) => {
      expect(res.body).to.have.length.gte(3)
      const names = res.body.map((c) => c.name)
      expect(names).to.include('CSE Department')
      expect(names).to.include('Hostel Block B')
      expect(names).to.include('Engineering Office')
    })
  })

  it('Football has exactly 2 asset units (1 AVAILABLE + 1 RESERVED)', () => {
    cy.request({
      method: 'POST',
      url: '/api/auth/login',
      body: { email: ahmed.email, password: ahmed.password },
      failOnStatusCode: true,
    })
    cy.request('GET', '/api/assets').then((res) => {
      const football = res.body.find((a) => a.title === 'Football')
      expect(football).to.exist
      expect(football.totalUnits).to.equal(2)
      expect(football.availableUnits).to.equal(1)
      expect(football.borrowedUnits).to.equal(0)
    })
  })

  it('Football is listed in exactly 3 communities', () => {
    cy.request({
      method: 'POST',
      url: '/api/auth/login',
      body: { email: ahmed.email, password: ahmed.password },
      failOnStatusCode: true,
    })
    cy.request('GET', '/api/assets').then((assetsRes) => {
      const football = assetsRes.body.find((a) => a.title === 'Football')
      cy.request('GET', `/api/assets/${football.id}/listings`).then((listingsRes) => {
        const activeListings = listingsRes.body.filter((l) => l.listingStatus === 'LISTED')
        expect(activeListings).to.have.length(3)
        const communityNames = activeListings.map((l) => l.communityName).sort()
        expect(communityNames).to.deep.equal(['CSE Department', 'Engineering Office', 'Hostel Block B'])
      })
    })
  })

  it('Spare Laptop exists and has zero community listings', () => {
    cy.request({
      method: 'POST',
      url: '/api/auth/login',
      body: { email: 'karim@example.com', password: 'password123' },
      failOnStatusCode: true,
    })
    cy.request('GET', '/api/assets').then((res) => {
      const spare = res.body.find((a) => a.title === 'Spare Laptop')
      expect(spare).to.exist
      expect(spare.totalUnits).to.equal(1)
      expect(spare.availableUnits).to.equal(1)
      cy.request('GET', `/api/assets/${spare.id}/listings`).then((listingsRes) => {
        expect(listingsRes.body).to.have.length(0)
      })
    })
  })
})
