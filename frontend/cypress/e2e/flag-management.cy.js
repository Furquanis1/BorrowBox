describe('Flag management (V2.4.2)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const marker = `E2EFLG242_${Date.now()}`

  let cseId
  let hostelId
  let cseTxnId

  before(() => {
    cy.request('POST', '/api/auth/login', ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
      hostelId = res.body.find((c) => c.name === 'Hostel Block B').id
    })
    cy.request('GET', '/api/me/lend-requests').then((res) => {
      cseTxnId = res.body.find((t) => Number(t.communityId) === cseId).id
    })
    cy.task('restoreFlags', marker)
  })

  beforeEach(() => {
    cy.task('restoreFlags', marker)
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
    cy.task('restoreFlags', marker)
  })

  it('should open a manual flag in the manager-only UI', () => {
    cy.visit(`/communities/${cseId}/flags`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/flags`)
    cy.get('.flags-page').should('be.visible')

    cy.contains('button', 'Open a flag').click()
    cy.get('#create-flag-type').select('MANUAL')
    cy.get('#create-flag-note').type(`${marker}-manual`)
    cy.get('form[aria-label="Open a flag"] button[type="submit"]').click()

    cy.contains('.flag-item', `${marker}-manual`).should('be.visible')
    cy.contains('.flag-item', `${marker}-manual`).should('contain', 'OPEN')
  })

  it('should update status, assign, and unassign a transaction flag', () => {
    cy.request('POST', `/api/communities/${cseId}/flags`, {
      flagType: 'OVERDUE',
      transactionId: cseTxnId,
      note: `${marker}-txn`,
    })

    cy.visit(`/communities/${cseId}/flags`)
    cy.contains('.flag-item', `${marker}-txn`).should('be.visible')
    cy.contains('.flag-item', `${marker}-txn`).should('contain', '#').should('contain', 'OVERDUE')

    cy.contains('.flag-item', `${marker}-txn`).find('select').select('REVIEWED')
    cy.contains('.flag-item', `${marker}-txn`).should('contain', 'REVIEWED')
    cy.contains('.flag-item', `${marker}-txn`).should('contain', 'Unassigned')

    cy.contains('.flag-item', `${marker}-txn`).contains('button', 'Assign to me').click()
    cy.contains('.flag-item', `${marker}-txn`).should('contain', 'Assigned to Ahmed')

    cy.contains('.flag-item', `${marker}-txn`).contains('button', 'Unassign').click()
    cy.contains('.flag-item', `${marker}-txn`).should('contain', 'Unassigned')
  })

  it('should filter flags by transaction id and clear, and expose no flags route to non-managers', () => {
    cy.request('POST', `/api/communities/${cseId}/flags`, {
      flagType: 'MANUAL',
      note: `${marker}-manual`,
    })
    cy.request('POST', `/api/communities/${cseId}/flags`, {
      flagType: 'OVERDUE',
      transactionId: cseTxnId,
      note: `${marker}-txn`,
    })

    cy.visit(`/communities/${cseId}/flags`)
    cy.contains('.flag-item', `${marker}-manual`).should('be.visible')
    cy.contains('.flag-item', `${marker}-txn`).should('be.visible')

    cy.get('#filter-flag-transaction').type(String(cseTxnId))
    cy.contains('button', 'Apply').click()
    cy.contains('.flag-item', `${marker}-manual`).should('not.exist')
    cy.contains('.flag-item', `${marker}-txn`).should('be.visible')

    cy.contains('button', 'Clear').click()
    cy.contains('.flag-item', `${marker}-manual`).should('be.visible')

    cy.visit(`/communities/${hostelId}/flags`)
    cy.url({ timeout: 15000 }).should('match', /\/communities\/\d+$/)
    cy.get('.community-home').should('be.visible')
    cy.get('.flags-page').should('not.exist')
  })
})