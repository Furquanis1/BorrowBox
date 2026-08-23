describe('Authentication Flow', () => {
  const testUser = {
    fullName: `E2E Test User ${Date.now()}`,
    email: `e2e-${Date.now()}@borrowbox.test`,
    password: 'TestPassword123!',
  }

  describe('Sign Up', () => {
    beforeEach(() => {
      cy.visit('/signup')
    })

    it('should display the sign up form', () => {
      cy.contains('Create Your Account').should('be.visible')
      cy.get('input[type="text"]').should('be.visible')
      cy.get('input[type="email"]').should('be.visible')
      cy.get('input[type="password"]').should('be.visible')
      cy.contains('Create Account').should('be.visible')
    })

    it('should show validation for empty required fields', () => {
      cy.contains('Create Account').click()
      // HTML5 validation should prevent submission
      cy.url().should('include', '/signup')
    })

    it('should navigate to sign in page via link', () => {
      cy.contains('Sign in').click()
      cy.url().should('include', '/signin')
    })

    it('should register a new user and redirect to dashboard', () => {
      cy.get('input[type="text"]').type(testUser.fullName)
      cy.get('input[type="email"]').type(testUser.email)
      cy.get('input[type="password"]').type(testUser.password)
      cy.get('form.auth-form button[type="submit"]').click()

      // Should redirect to dashboard after successful registration
      cy.url().should('include', '/dashboard', { timeout: 15000 })
      cy.contains(testUser.fullName).should('be.visible')
    })
  })

  describe('Sign In', () => {
    beforeEach(() => {
      cy.visit('/signin')
    })

    it('should display the sign in form', () => {
      cy.contains('Welcome Back').should('be.visible')
      cy.get('input[type="email"]').should('be.visible')
      cy.get('input[type="password"]').should('be.visible')
      cy.contains('Sign In').should('be.visible')
    })

    it('should show error for invalid credentials', () => {
      cy.intercept('POST', '**/api/auth/login').as('login')

      cy.get('input[type="email"]').type('nonexistent@example.com')
      cy.get('input[type="password"]').type('WrongPassword123!')
      cy.get('form.auth-form button[type="submit"]').click()

      cy.wait('@login').then((interception) => {
        expect(interception.response.statusCode).to.eq(401)
        expect(interception.response.body.error).to.eq('Invalid email or password')
      })

      cy.contains('Invalid email or password').should('be.visible')
      cy.url().should('include', '/signin')
    })

    it('should navigate to sign up page via link', () => {
      cy.contains('Create one').click()
      cy.url().should('include', '/signup')
    })

    it('should sign in with valid credentials and redirect to dashboard', () => {
      cy.get('input[type="email"]').type(testUser.email)
      cy.get('input[type="password"]').type(testUser.password)
      cy.get('form.auth-form button[type="submit"]').click()

      cy.url().should('include', '/dashboard', { timeout: 15000 })
      cy.contains(testUser.fullName).should('be.visible')
    })
  })

  describe('Protected Routes', () => {
    it('should redirect unauthenticated users from dashboard to sign in', () => {
      // Clear cookies to ensure no session
      cy.clearCookies()
      cy.visit('/dashboard')
      cy.url().should('include', '/signin')
    })
  })
})
