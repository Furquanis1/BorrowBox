describe('Community rules page (V2.4.3)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }
  const RULE_TYPES = [
    'MEMBERSHIP_CONTEXT_FIELDS',
    'MAX_ACTIVE_MEMBERS',
    'ADMISSION_NOTE',
    'OVERDUE_GRACE_PERIOD',
  ]

  let cseId

  const restoreAllRules = () => {
    cy.wrap(RULE_TYPES).each((ruleType) => {
      cy.task('restoreRules', { communityId: cseId, ruleType })
    })
  }

  before(() => {
    cy.request('POST', '/api/auth/login', ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
    })
  })

  beforeEach(() => {
    cy.then(restoreAllRules)
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
    cy.then(restoreAllRules)
  })

  const visitRules = () => {
    cy.visit(`/communities/${cseId}/rules`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/rules`)
    cy.get('.rules-page').should('be.visible')
  }

  const submitRule = () => {
    cy.get('form[aria-label="Create a rule"] button[type="submit"]').click()
  }

  it('should render the right editor per rule type with no raw JSON textarea', () => {
    visitRules()
    cy.contains('button', 'Create rule').click()

    cy.get('#create-rule-type').select('MEMBERSHIP_CONTEXT_FIELDS')
    cy.get('#create-context-field-program').should('exist')
    cy.get('#create-context-field-program').should('be.visible')
    cy.get('#create-context-field-tower').should('exist')

    cy.get('#create-rule-type').select('MAX_ACTIVE_MEMBERS')
    cy.get('#create-max-members').should('exist')
    cy.get('#create-max-members').should('be.visible')

    cy.get('#create-rule-type').select('ADMISSION_NOTE')
    cy.get('#create-admission-note').should('exist')
    cy.get('#create-admission-note').should('be.visible')

    cy.get('#create-rule-type').select('OVERDUE_GRACE_PERIOD')
    cy.get('#create-grace-days').should('exist')
    cy.get('#create-grace-days').should('be.visible')

    cy.get('#create-rule-value').should('not.exist')
  })

  it('should create a MEMBERSHIP_CONTEXT_FIELDS rule from checkboxes, not JSON', () => {
    visitRules()
    cy.contains('button', 'Create rule').click()

    cy.get('#create-rule-type').select('MEMBERSHIP_CONTEXT_FIELDS')
    cy.get('#create-context-field-program').check()
    cy.get('#create-context-field-year').check()
    submitRule()

    const item = () => cy.contains('.rule-item', 'MEMBERSHIP_CONTEXT_FIELDS')
    item().should('be.visible')
    item().should('contain', 'ACTIVE')
    item().should('contain', 'Program, Year')
  })

  it('should create a MAX_ACTIVE_MEMBERS rule with a number', () => {
    visitRules()
    cy.contains('button', 'Create rule').click()

    cy.get('#create-rule-type').select('MAX_ACTIVE_MEMBERS')
    cy.get('#create-max-members').clear().type('120')
    submitRule()

    const item = () => cy.contains('.rule-item', 'MAX_ACTIVE_MEMBERS')
    item().should('contain', 'ACTIVE')
    item().should('contain', 'max 120 members')
  })

  it('should create an ADMISSION_NOTE rule with plain text', () => {
    visitRules()
    cy.contains('button', 'Create rule').click()

    cy.get('#create-rule-type').select('ADMISSION_NOTE')
    cy.get('#create-admission-note').clear().type('Please return items clean and on time.')
    submitRule()

    const item = () => cy.contains('.rule-item', 'ADMISSION_NOTE')
    item().should('contain', 'ACTIVE')
    item().should('contain', 'Please return items clean and on time.')
  })

  it('should create an OVERDUE_GRACE_PERIOD rule with a days number', () => {
    visitRules()
    cy.contains('button', 'Create rule').click()

    cy.get('#create-rule-type').select('OVERDUE_GRACE_PERIOD')
    cy.get('#create-grace-days').clear().type('3')
    submitRule()

    const item = () => cy.contains('.rule-item', 'OVERDUE_GRACE_PERIOD')
    item().should('be.visible')
    item().should('contain', 'ACTIVE')
    item().should('contain', '3 day grace')
  })

  it('should validate malformed input before submitting', () => {
    visitRules()
    cy.contains('button', 'Create rule').click()

    cy.get('#create-rule-type').select('OVERDUE_GRACE_PERIOD')
    cy.get('#create-grace-days').clear().type('120')
    submitRule()

    cy.get('form[aria-label="Create a rule"] .field-error', { timeout: 10000 })
      .should('be.visible')
      .should('contain', 'between 0 and 30')

    cy.get('#create-rule-type').select('MEMBERSHIP_CONTEXT_FIELDS')
    submitRule()
    cy.get('form[aria-label="Create a rule"] .field-error')
      .should('be.visible')
      .should('contain', 'at least one')
  })

  it('should show existing rules as readable summaries, not JSON', () => {
    visitRules()

    cy.contains('button', 'Create rule').click()
    cy.get('#create-rule-type').select('MAX_ACTIVE_MEMBERS')
    cy.get('#create-max-members').clear().type('50')
    submitRule()
    cy.contains('.rule-item', 'MAX_ACTIVE_MEMBERS').should('contain', 'max 50 members')
    cy.contains('.rule-item', 'MAX_ACTIVE_MEMBERS').should('not.contain', '{')
  })

  it('should let a manager deactivate and reactivate a rule', () => {
    visitRules()

    cy.contains('button', 'Create rule').click()
    cy.get('#create-rule-type').select('OVERDUE_GRACE_PERIOD')
    cy.get('#create-grace-days').clear().type('5')
    submitRule()

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
    cy.get('.rules-page').contains('button', 'Create rule').should('not.exist')
    cy.get('.rules-page').contains('button', 'Deactivate').should('not.exist')
  })
})