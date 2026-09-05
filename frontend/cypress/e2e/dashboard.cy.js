describe('Dashboard Routing & Shell', () => {
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
    cy.url().should('include', '/dashboard/explore', { timeout: 15000 })
  })

  it('should redirect /dashboard to /dashboard/explore', () => {
    cy.visit('/dashboard')
    cy.url({ timeout: 15000 }).should('include', '/dashboard/explore')
  })

  it('should display the sidebar shell with user info and all nav links', () => {
    cy.get('.dashboard-sidebar').should('be.visible')
    cy.contains(testUser.fullName).should('be.visible')
    ;['Explore', 'Inventory', 'Requests', 'Loans', 'Members', 'Rules'].forEach((label) => {
      cy.get('.dashboard-sidebar').contains('a', label).should('be.visible')
    })
  })

  it('should mark the active NavLink with aria-current', () => {
    cy.get('.dashboard-sidebar .nav-item[aria-current="page"]').should('contain', 'Explore')
    cy.get('.dashboard-sidebar').contains('a', 'Inventory').click()
    cy.url().should('include', '/dashboard/inventory')
    cy.get('.dashboard-sidebar .nav-item[aria-current="page"]').should('contain', 'Inventory')
  })

  it('should navigate between nested routes and show the matching page', () => {
    cy.get('.dashboard-sidebar').contains('a', 'Inventory').click()
    cy.url().should('include', '/dashboard/inventory')
    cy.get('.topbar-title').should('contain', 'My Inventory')

    cy.get('.dashboard-sidebar').contains('a', 'Requests').click()
    cy.url().should('include', '/dashboard/requests')
    cy.get('.topbar-title').should('contain', 'Borrow Requests')

    cy.get('.dashboard-sidebar').contains('a', 'Loans').click()
    cy.url().should('include', '/dashboard/loans')
    cy.get('.topbar-title').should('contain', 'Active Loans')
  })

  it('should navigate to Members and Rules pages', () => {
    cy.get('.dashboard-sidebar').contains('a', 'Members').click()
    cy.url().should('include', '/dashboard/members')
    cy.get('.topbar-title').should('contain', 'Members')

    cy.get('.dashboard-sidebar').contains('a', 'Rules').click()
    cy.url().should('include', '/dashboard/rules')
    cy.get('.topbar-title').should('contain', 'Community Rules')
  })

  it('should support deep-linking to a nested route after authentication', () => {
    cy.visit('/dashboard/members')
    cy.url({ timeout: 15000 }).should('include', '/dashboard/members')
    cy.get('.topbar-title').should('contain', 'Members')
  })

  it('should preserve the nested route on browser refresh', () => {
    cy.visit('/dashboard/inventory')
    cy.url({ timeout: 15000 }).should('include', '/dashboard/inventory')
    cy.get('.topbar-title').should('contain', 'My Inventory')

    cy.reload()
    cy.get('.topbar-title', { timeout: 15000 }).should('contain', 'My Inventory')
    cy.url().should('include', '/dashboard/inventory')
  })

  it('desktop shell should show the sidebar and hide the bottom nav', () => {
    cy.viewport(1280, 720)
    cy.get('.dashboard-sidebar').should('be.visible')
    cy.get('.bottom-nav').should('not.be.visible')
    cy.get('.topbar-more').should('not.be.visible')
  })

  it('mobile shell should show topbar and bottom nav and hide the sidebar', () => {
    cy.viewport('iphone-x')
    cy.get('.dashboard-sidebar').should('not.be.visible')
    cy.get('.dashboard-topbar').should('be.visible')
    cy.get('.bottom-nav').should('be.visible')
    ;['Explore', 'Inventory', 'Requests', 'Loans', 'More'].forEach((label) => {
      cy.get('.bottom-nav').contains(label).should('be.visible')
    })
  })

  it('tablet shell should keep the sidebar and hide the bottom nav', () => {
    cy.viewport(1024, 768)
    cy.get('.dashboard-sidebar').should('be.visible')
    cy.get('.bottom-nav').should('not.be.visible')
    cy.get('.topbar-more').should('not.be.visible')
  })

  it('skip link should move focus to main content', () => {
    cy.get('.skip-link').trigger('click', { force: true })
    cy.get('#main-content').should('have.focus')
  })

  it('mobile More drawer should expose Members and Rules and close on navigation', () => {
    cy.viewport('iphone-x')
    cy.get('.topbar-more').click()
    cy.get('.more-drawer').should('be.visible')
    cy.get('.more-drawer').contains('Members').should('be.visible')
    cy.get('.more-drawer').contains('Rules').should('be.visible')

    cy.get('.more-drawer').contains('a', 'Rules').click()
    cy.url().should('include', '/dashboard/rules')
    cy.get('.more-drawer').should('not.exist')
    cy.get('.topbar-title').should('contain', 'Community Rules')
  })

  it('mobile More drawer should close on Escape', () => {
    cy.viewport('iphone-x')
    cy.get('.topbar-more').click()
    cy.get('.more-drawer').should('be.visible')
    cy.get('.more-drawer .icon-button').should('have.focus')
    cy.get('body').type('{esc}')
    cy.get('.more-drawer').should('not.exist')
  })

  it('should sign out and redirect to the landing page', () => {
    cy.contains('Sign Out').click()
    cy.location('pathname', { timeout: 15000 }).should((pathname) => {
      expect(['/', '/signin']).to.include(pathname)
    })
  })
})