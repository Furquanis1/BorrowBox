describe('Manager dashboard (V2.4.2)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const marker = `E2EMGD242_${Date.now()}`

  let cseId
  let hostelId

  before(() => {
    cy.request('POST', '/api/auth/login', ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
      hostelId = res.body.find((c) => c.name === 'Hostel Block B').id
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

  it('should render manager-only overview cards matching the dashboard API', () => {
    cy.request('POST', `/api/communities/${cseId}/flags`, {
      flagType: 'MANUAL',
      note: marker,
    })

    cy.visit(`/communities/${cseId}/dashboard`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/dashboard`)
    cy.get('.dashboard-page').should('be.visible')

    cy.request('GET', `/api/communities/${cseId}/dashboard`).then((res) => {
      const body = res.body
      cy.get('.stat-active-loans .stat-value').should('have.text', String(body.activeLoanCount))
      cy.get('.stat-overdue .stat-value').first().should('have.text', String(body.overdueLoanCount))
      cy.get('.stat-pending-members .stat-value').should('have.text', String(body.pendingMembershipCount))
      cy.get('.stat-active-members .stat-value').first().should('have.text', String(body.activeMemberCount))
      cy.get('.stat-open-flags .stat-value').first().should('have.text', String(body.openFlagCount))
      cy.get('.stat-completed-loans .stat-value').should('have.text', String(body.completedLoansCount))
      cy.get('.stat-volume-30d .stat-value').should('have.text', String(body.transactionVolume30d))
    })

    cy.get('.community-health-card').should('be.visible')
    cy.get('.community-health-card .stat-active-members .stat-value')
      .invoke('text')
      .should('match', /^\d+$/)
    cy.get('.activity-list').should('be.visible')
    cy.contains('.flag-chip', 'MANUAL').should('contain', 'Unassigned')
  })

  it('should redirect non-managers away from the dashboard route', () => {
    cy.visit(`/communities/${hostelId}/dashboard`)
    cy.url({ timeout: 15000 }).should('match', /\/communities\/\d+$/)
    cy.get('.community-home').should('be.visible')
    cy.get('.dashboard-page').should('not.exist')
  })

  after(() => {
    cy.task('restoreFlags', marker)
  })
})