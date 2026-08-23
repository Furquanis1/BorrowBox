describe('Dashboard', () => {
  const testUser = {
    fullName: `Dashboard User ${Date.now()}`,
    email: `dashboard-${Date.now()}@borrowbox.test`,
    password: 'TestPassword123!',
  }

  before(() => {
    // Register a fresh user via the API for dashboard tests
    cy.request('POST', '/api/auth/register', {
      fullName: testUser.fullName,
      email: testUser.email,
      password: testUser.password,
    })
  })

  beforeEach(() => {
    // Sign in via UI before each test
    cy.clearCookies()
    cy.clearLocalStorage()
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').type(testUser.email)
    cy.get('input[type="password"]').type(testUser.password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/dashboard', { timeout: 15000 })
  })

  it('should display the dashboard sidebar with navigation', () => {
    cy.get('.dashboard-sidebar').should('be.visible')
    cy.contains('BorrowBox').should('be.visible')
    cy.contains(testUser.fullName).should('be.visible')
  })

  it('should display navigation tabs', () => {
    cy.contains('Explore').should('be.visible')
    cy.contains('Inventory').should('be.visible')
    cy.contains('Requests').should('be.visible')
    cy.contains('Loans').should('be.visible')
  })

  it('should default to the Explore Items tab', () => {
    cy.contains('Explore Items').should('be.visible')
  })

  it('should switch to Inventory tab', () => {
    cy.contains('Inventory').click()
    cy.contains('My Inventory').should('be.visible')
  })

  it('should switch to Requests tab', () => {
    cy.contains('Requests').click()
    cy.contains('Borrow Requests').should('be.visible')
  })

  it('should switch to Loans tab', () => {
    cy.contains('Loans').click()
    cy.contains('Active Loans').should('be.visible')
  })

  it('should sign out and redirect to landing page', () => {
    cy.contains('Sign Out').click()
    cy.url().should('eq', Cypress.config('baseUrl') + '/')
  })
})
