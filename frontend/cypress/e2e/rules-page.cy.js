describe('Community rules page (V2.4.2)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }

  let cseId

  before(() => {
    cy.request('POST', '/api/auth/login', ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
    })
    cy.then(() => cy.task('restoreRules', { communityId: cseId, ruleType: 'OVERDUE_GRACE_PERIOD' }))
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

  after(() => {
    cy.then(() => cy.task('restoreRules', { communityId: cseId, ruleType: 'OVERDUE_GRACE_PERIOD' }))
  })

  it('should let a manager create and activate a grace-period rule', () => {
    cy.visit(`/communities/${cseId}/rules`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/rules`)
    cy.get('.rules-page').should('be.visible')

    cy.contains('button', 'Create rule').click()
    cy.get('#create-rule-type').select('OVERDUE_GRACE_PERIOD')
    cy.get('#create-rule-value').clear().type('{ "days": 5 }', { parseSpecialCharSequences: false })
    cy.get('form[aria-label="Create a rule"] button[type="submit"]').click()

    cy.contains('.rule-item', 'OVERDUE_GRACE_PERIOD').should('be.visible')
    cy.contains('.rule-item', 'OVERDUE_GRACE_PERIOD').should('contain', 'ACTIVE')
    cy.contains('.rule-item', 'OVERDUE_GRACE_PERIOD').should('contain', '5 day grace')
  })

  it('should let a manager deactivate and reactivate a rule', () => {
    cy.visit(`/communities/${cseId}/rules`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/rules`)

    const ruleItem = () => cy.contains('.rule-item', 'OVERDUE_GRACE_PERIOD')
    ruleItem().should('contain', 'ACTIVE')
    ruleItem().contains('button', 'Deactivate').click()
    ruleItem().should('contain', 'ARCHIVED')

    ruleItem().contains('button', 'Activate').click()
    ruleItem().should('contain', 'ACTIVE')
  })

  it('should show active rules read-only to members', () => {
    cy.clearCookies()
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').type(salah.email)
    cy.get('input[type="password"]').type(salah.password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/communities/', { timeout: 15000 })

    cy.visit(`/communities/${cseId}/rules`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/rules`)
    cy.get('.rules-page').should('be.visible')
    cy.contains('.rule-item', 'OVERDUE_GRACE_PERIOD').should('contain', 'ACTIVE')
    cy.get('.rules-page').contains('button', 'Create rule').should('not.exist')
    cy.get('.rules-page').contains('button', 'Deactivate').should('not.exist')
  })
})
