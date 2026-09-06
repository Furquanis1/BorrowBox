describe('Community Listings (V2.1.5)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const stamp = Date.now()
  const assetTitle = `Drill ${stamp}`
  let drillId

  before(() => {
    // The seeded owner needs an owned asset that isn't pre-listed anywhere,
    // so the creation flow below is genuinely exercised end-to-end.
    cy.request('POST', '/api/auth/login', {
      email: ahmed.email,
      password: ahmed.password,
    })
    cy.request('POST', '/api/assets', {
      title: assetTitle,
      description: 'E2E listing test asset',
      categoryId: null,
      quantity: 2,
    }).then((res) => {
      drillId = res.body.id
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
    cy.url().should('include', '/dashboard/explore', { timeout: 15000 })
  })

  it('lists an owned asset in a community and surfaces it in Explore', () => {
    cy.get('.dashboard-sidebar').contains('a', 'Inventory').click()
    cy.contains('.asset-row', assetTitle).should('be.visible')
    cy.contains('.asset-row', assetTitle).should('contain', 'Not listed')
    cy.contains('.asset-row', assetTitle).contains('button', 'List in community').click()

    cy.get('.listing-drawer').should('be.visible')
    cy.contains('.listing-community-row', 'CSE Department').contains('button', 'List').click()
    cy.get('.toast-success').should('be.visible')

    cy.get('.listing-drawer').contains('h4', 'Currently listed').should('be.visible')
    cy.contains('.listing-community-row', 'CSE Department').should('contain', 'Listed')

    cy.get('.listing-drawer .icon-button').click()
    cy.get('.listing-drawer').should('not.exist')
    cy.contains('.asset-row', assetTitle).should('contain', 'Listed in 1 community')

    cy.get('.dashboard-sidebar').contains('a', 'Explore').click()
    cy.contains('.group-item', 'CSE Department').click()
    cy.contains('.explore-listing-card', assetTitle).should('be.visible')
    cy.contains('.explore-listing-card', assetTitle).should('contain', '2 available')
    cy.contains('.explore-listing-card', assetTitle).should('contain', '0 borrowed')
  })

  it('supports soft unlist and re-listing', () => {
    cy.get('.dashboard-sidebar').contains('a', 'Inventory').click()
    cy.contains('.asset-row', assetTitle).contains('button', 'List in community').click()

    cy.get('.listing-drawer').should('be.visible')
    cy.contains('.listing-community-row', 'CSE Department').contains('button', 'Unlist').click()
    cy.get('.toast-success').should('be.visible')

    cy.get('.listing-drawer').contains('h4', 'List in a community').should('be.visible')
    cy.contains('.listing-community-row', 'CSE Department').should('contain', 'Active member')
    cy.get('.listing-drawer .icon-button').click()
    cy.contains('.asset-row', assetTitle).should('contain', 'Not listed')

    cy.get('.dashboard-sidebar').contains('a', 'Explore').click()
    cy.contains('.group-item', 'CSE Department').click()
    cy.contains('.explore-listing-card', assetTitle).should('not.exist')

    cy.get('.dashboard-sidebar').contains('a', 'Inventory').click()
    cy.contains('.asset-row', assetTitle).contains('button', 'List in community').click()
    cy.contains('.listing-community-row', 'CSE Department').contains('button', 'List').click()
    cy.get('.toast-success').should('be.visible')
    cy.get('.listing-drawer .icon-button').click()
    cy.contains('.asset-row', assetTitle).should('contain', 'Listed in 1 community')

    cy.get('.dashboard-sidebar').contains('a', 'Explore').click()
    cy.contains('.group-item', 'CSE Department').click()
    cy.contains('.explore-listing-card', assetTitle).should('be.visible')
  })

  it('rejects a duplicate listing with 400 from the API', () => {
    cy.request('GET', '/api/communities').then((res) => {
      const cse = res.body.find((c) => c.name === 'CSE Department')
      cy.wrap(cse).its('id').then((cseId) => {
        // Reset to a knowable state, then prove create + duplicate rejection.
        cy.request({
          method: 'DELETE',
          url: `/api/assets/${drillId}/listings/${cseId}`,
          failOnStatusCode: false,
        }).then((r) => cy.wrap(r.status).should('eq', 200))

        cy.request({
          method: 'POST',
          url: `/api/assets/${drillId}/listings`,
          body: { communityId: cseId },
          failOnStatusCode: false,
        }).then((r) => cy.wrap(r.status).should('be.oneOf', [200, 201]))

        cy.request({
          method: 'POST',
          url: `/api/assets/${drillId}/listings`,
          body: { communityId: cseId },
          failOnStatusCode: false,
        }).then((r) => {
          cy.wrap(r.status).should('eq', 400)
          cy.wrap(r.body).its('error').should('contain', 'already listed')
        })
      })
    })
  })

  it('rejects non-members and non-owners with 401', () => {
    // Resolve community ids while signed in as the seed owner (Ahmed).
    cy.request('GET', '/api/communities').then((res) => {
      const cseId = res.body.find((c) => c.name === 'CSE Department').id
      const officeId = res.body.find((c) => c.name === 'Engineering Office').id

      // A fresh, memberless owner cannot list into or view any community.
      const fresh = {
        fullName: `Fresh ${Date.now()}`,
        email: `fresh-${Date.now()}@borrowbox.test`,
        password: 'TestPassword123!',
      }

      cy.request('POST', '/api/auth/register', fresh)
      cy.request('POST', '/api/assets', {
        title: `Fresh Asset ${Date.now()}`,
        description: 'owned but memberless asset',
        categoryId: null,
        quantity: 1,
      }).then((assetRes) => {
        const freshAssetId = assetRes.body.id
        expect(freshAssetId, 'fresh asset id').to.exist

        cy.request({ url: `/api/communities/${cseId}/listings`, failOnStatusCode: false })
          .then((r) => cy.wrap(r.status).should('eq', 401))
        cy.request({
          method: 'POST',
          url: `/api/assets/${freshAssetId}/listings`,
          body: { communityId: cseId },
          failOnStatusCode: false,
        }).then((r) => cy.wrap(r.status).should('eq', 401))
      })

      // Karim is an ACTIVE member of Engineering Office only.
      cy.request('POST', '/api/auth/login', {
        email: 'karim@example.com',
        password: 'password123',
      })
      cy.request({ url: `/api/communities/${cseId}/listings`, failOnStatusCode: false })
        .then((r) => cy.wrap(r.status).should('eq', 401))
      cy.request({ url: `/api/communities/${officeId}/listings` }).then((r) => {
        const calculator = r.body.find((l) => l.title === 'Scientific Calculator')
        expect(calculator, 'calculator listed in Engineering Office').to.exist
        expect(calculator.listingStatus, 'listingStatus').to.equal('LISTED')
      })
    })
  })
})

describe('Shared inventory availability is server authoritative', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }

  beforeEach(() => {
    cy.clearCookies()
    cy.clearLocalStorage()
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').type(ahmed.email)
    cy.get('input[type="password"]').type(ahmed.password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/dashboard/explore', { timeout: 15000 })
  })

  it('shows identical Football availability across its shared communities', () => {
    const readCardText = (communityName) => {
      cy.contains('.group-item', communityName).click()
      const card = cy.contains('.explore-listing-card', 'Football')
      card.should('be.visible')
      return card.invoke('text').then((t) => t.trim())
    }

    const captures = {}
    ;['CSE Department', 'Hostel Block B', 'Engineering Office'].forEach((name) => {
      readCardText(name).then((text) => {
        captures[name] = text
      })
    })

    cy.then(() => {
      const cse = captures['CSE Department']
      expect(captures['Hostel Block B']).to.equal(cse)
      expect(captures['Engineering Office']).to.equal(cse)
    })
  })
})