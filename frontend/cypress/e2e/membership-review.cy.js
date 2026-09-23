describe('Membership review (V2.4.2)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }
  const marker = `E2EMBR242_${Date.now()}`
  const memberEmail = `${marker.toLowerCase()}@borrowbox.test`

  let cseId
  let throwawayId

  before(() => {
    cy.request('POST', '/api/auth/login', ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
    })

    cy.request('POST', '/api/auth/register', {
      fullName: marker,
      email: memberEmail,
      password: 'TestPassword123!',
    }).then((res) => {
      throwawayId = res.body.user.id
    })

    cy.request('POST', '/api/auth/login', {
      email: memberEmail,
      password: 'TestPassword123!',
    })
    cy.then(() => {
      cy.request('POST', `/api/communities/${cseId}/join`, {})
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

  after(() => {
    cy.then(() => cy.task('restoreMembership', { userId: throwawayId, communityId: cseId }))
  })

  // The global event envelope renders one unread delivery after the route's
// event fetch settles, so it can land a moment after the page looks ready.
// Dismiss it whenever it appears so its toast never covers the member row
// action buttons; at most one envelope is presented per page load.
const dismissEnvelope = () => {
    const poll = (attempt) => {
      cy.document().then((doc) => {
        if (doc.querySelector('.event-envelope-later')) {
          cy.get('.event-envelope-later').click()
        } else if (attempt < 6) {
          cy.wait(500).then(() => poll(attempt + 1))
        }
      })
    }
    poll(0)
  }

  it('should show the pending request and approve it', () => {
    cy.visit(`/communities/${cseId}/members`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/members`)

    cy.get('.members-page').should('be.visible')
    dismissEnvelope()
    cy.get('.members-page').should('contain', 'Pending requests')
    cy.contains('.member-item', marker).should('contain', 'PENDING')

    cy.contains('.member-item', marker).contains('button', 'Approve').click()
    cy.contains('.member-item', marker).should('contain', 'ACTIVE')
  })

  it('should suspend and reinstate an active member', () => {
    cy.visit(`/communities/${cseId}/members`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/members`)

    dismissEnvelope()
    cy.contains('.member-item', marker).should('contain', 'ACTIVE')
    cy.contains('.member-item', marker).contains('button', 'Suspend').click()
    cy.contains('.member-item', marker).should('contain', 'SUSPENDED')

    dismissEnvelope()
    cy.contains('.member-item', marker).contains('button', 'Reinstate').click()
    cy.contains('.member-item', marker).should('contain', 'ACTIVE')
  })

  it('should remove an active member', () => {
    cy.visit(`/communities/${cseId}/members`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/members`)

    dismissEnvelope()
    cy.contains('.member-item', marker).should('contain', 'ACTIVE')
    cy.contains('.member-item', marker).contains('button', 'Remove').click()
    cy.contains('.member-item', marker).should('contain', 'LEFT')
  })

  it('should hide moderation controls for non-manager members', () => {
    cy.clearCookies()
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').type(salah.email)
    cy.get('input[type="password"]').type(salah.password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/communities/', { timeout: 15000 })

    cy.visit(`/communities/${cseId}/members`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/members`)
    cy.get('.members-page').should('be.visible')
    cy.get('.member-item').should('have.length.at.least', 1)
    cy.get('.members-page').should('not.contain', 'Pending requests')
    cy.get('.members-page').contains('button', 'Suspend').should('not.exist')
    cy.get('.members-page').contains('button', 'Remove').should('not.exist')
  })
})