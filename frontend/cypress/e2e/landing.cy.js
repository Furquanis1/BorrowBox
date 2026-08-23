describe('Landing Page', () => {
  beforeEach(() => {
    cy.visit('/')
  })

  it('should display the landing page with branding', () => {
    cy.contains('BorrowBox').should('be.visible')
    cy.contains('Manage Borrowing & Lending Simply').should('be.visible')
  })

  it('should display the "How It Works" steps', () => {
    cy.contains('How It Works').should('be.visible')
    cy.contains('Add Items').should('be.visible')
    cy.contains('Request & Approve').should('be.visible')
    cy.contains('Track & Return').should('be.visible')
  })

  it('should have a working "Start Now" button that navigates to sign in', () => {
    cy.contains('Start Now').click()
    cy.url().should('include', '/signin')
  })
})
