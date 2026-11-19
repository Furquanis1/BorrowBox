describe('Community lifecycle (self-service create / join / leave)', () => {
  const marker = `E2ECLF${Date.now()}`
  const manager = {
    fullName: `${marker} Manager`,
    email: `${marker.toLowerCase()}-manager@borrowbox.test`,
    password: Cypress.env('testUserPassword'),
  }
  const joiner = {
    fullName: `${marker} Joiner`,
    email: `${marker.toLowerCase()}-joiner@borrowbox.test`,
    password: Cypress.env('testUserPassword'),
  }
  const communityName = `${marker} Community`

  let communityId
  let managerUserId
  let joinerUserId

  const signInAs = (user) => {
    cy.clearCookies()
    cy.clearLocalStorage()
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible').clear().type(user.email)
    cy.get('input[type="password"]').clear().type(user.password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('match', /\/(communities\/\d+|me\/inventory)/, { timeout: 15000 })
  }

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

  before(() => {
    cy.request('POST', '/api/auth/register', manager).then((res) => {
      managerUserId = res.body.user.id
    })
    cy.request('POST', '/api/auth/register', joiner).then((res) => {
      joinerUserId = res.body.user.id
    })
  })

  after(() => {
    if (communityId && managerUserId && joinerUserId) {
      cy.task('restoreCommunityLifecycle', {
        communityId,
        userIds: [managerUserId, joinerUserId],
      })
    }
  })

  it('creates a community from the empty state and enters Home as Manager', () => {
    signInAs(manager)

    cy.get('.group-empty').should('contain', 'No communities yet.')
    cy.get('.group-empty').contains('button', 'Create a community').click()

    cy.get('.lifecycle-modal', { timeout: 15000 }).should('be.visible')
    cy.get('.lifecycle-tab[aria-selected="true"]').should('contain', 'Create Community')
    cy.get('.lifecycle-input').first().type(communityName)
    cy.get('.lifecycle-textarea').type('A self-service onboarding fixture.')
    cy.get('.lifecycle-select').first().select('COLLEGE')
    cy.get('.lifecycle-select').eq(1).should('have.value', 'MANAGER_APPROVAL')

    cy.intercept('POST', '**/api/communities').as('createCommunity')
    cy.get('.lifecycle-modal').contains('button', 'Create community').click()

    cy.wait('@createCommunity').then(({ response }) => {
      expect(response.statusCode).to.eq(201)
      expect(response.body.name).to.eq(communityName)
      expect(response.body.admissionMode).to.eq('MANAGER_APPROVAL')
      communityId = response.body.id
      return cy.url().should('include', `/communities/${communityId}`, { timeout: 15000 })
    })
    cy.get('.community-header-title').should('have.text', communityName)
    cy.get('.community-header-meta').should('contain', 'Manager')
    cy.get('.toast-success').should('be.visible').and('contain', 'Community created')
    cy.get('.lifecycle-modal').should('not.exist')
    cy.get('.rail-add-pill').should('contain', 'Add or join').should('be.visible')
    cy.get('.desktop-side-rail').should('be.visible').then(($rail) => {
      cy.get('.rail-add-pill').then(($pill) => {
        const pill = $pill[0]
        const rail = $rail[0]
        expect(pill.scrollWidth).to.be.at.most(pill.clientWidth)
        expect(pill.getBoundingClientRect().right).to.be.at.most(rail.getBoundingClientRect().right - 12)
      })
    })
  })

  it('lets a second user request to join strictly by exact Community ID', () => {
    signInAs(joiner)

    cy.intercept('GET', '**/api/communities/discover').as('discoverCommunities')

    cy.get('.group-empty').contains('button', 'Join a community').click()
    cy.get('.lifecycle-modal', { timeout: 15000 }).should('be.visible')
    cy.get('.lifecycle-tab[aria-selected="true"]').should('contain', 'Join Community')
    cy.get('.lifecycle-candidate').should('not.exist')
    cy.get('.lifecycle-hint').should('contain', 'never suggested or browsable')

    cy.get('.lifecycle-search-input').type('not-a-number')
    cy.get('.lifecycle-modal').contains('button', 'Find community').click()
    cy.get('.lifecycle-error').should('contain', 'numeric community ID')
    cy.get('.lifecycle-candidate').should('not.exist')

    cy.get('.lifecycle-search-input').clear().type(String(communityId))
    cy.get('.lifecycle-modal').contains('button', 'Find community').click()
    cy.get('.lifecycle-candidate').should('contain', communityName)
    cy.get('@discoverCommunities.all').should('have.length', 0)

    cy.intercept('POST', `**/api/communities/${communityId}/join`).as('joinCommunity')
    cy.get('.lifecycle-candidate').contains('button', 'Request to join').click()

    cy.wait('@joinCommunity').then(({ response }) => {
      expect(response.statusCode).to.eq(200)
      expect(response.body.status).to.eq('PENDING')
    })

    cy.get('.lifecycle-result').should('contain', 'Request sent — pending manager approval')
    cy.get('.toast-success').should('contain', 'Request sent — pending manager approval')
    cy.get('.lifecycle-modal').contains('button', 'Done').click()
    cy.contains('.group-item', communityName).should('contain', 'Pending approval')
  })

  it('notifies managers and applicants, then lets an approved member leave', () => {
    signInAs(manager)

    cy.request('GET', '/api/me/notifications').then((res) => {
      const notification = res.body.find((item) =>
        item.type === 'MEMBERSHIP_REQUEST_PENDING'
        && item.message.includes(joiner.fullName)
        && item.message.includes(communityName))
      expect(notification, 'manager join-request notification').to.exist
    })
    cy.get('.notification-bell-btn').click()
    cy.get('.notification-dropdown').should('contain', `New join request · ${communityName}`)
    cy.get('.notification-item.membership-notification').should('exist')
    cy.get('.notification-bell-btn').click()

    cy.visit(`/communities/${communityId}/members`)
    cy.url().should('include', `/communities/${communityId}/members`, { timeout: 15000 })
    dismissEnvelope()
    cy.contains('.member-item', joiner.fullName).contains('button', 'Approve').click()
    cy.contains('.member-item', joiner.fullName).should('contain', 'ACTIVE')

    signInAs(joiner)

    cy.request('GET', '/api/me/notifications').then((res) => {
      const notification = res.body.find((item) =>
        item.type === 'MEMBERSHIP_APPROVED' && item.message.includes(communityName))
      expect(notification, 'applicant approval notification').to.exist
    })
    cy.get('.notification-bell-btn').click()
    cy.get('.notification-dropdown').should('contain', `You're in · ${communityName}`)
    cy.get('.notification-item.membership-notification').should('exist')
    cy.get('.notification-bell-btn').click()

    cy.visit(`/communities/${communityId}`)
    cy.url().should('include', `/communities/${communityId}`, { timeout: 15000 })
    cy.get('.community-header-title').should('have.text', communityName)
    cy.get('.community-header-meta').should('contain', 'Member')

    cy.intercept('POST', `**/api/communities/${communityId}/leave`).as('leaveCommunity')
    cy.get('.community-header').contains('button', 'Leave community').click()
    cy.get('.community-leave-confirm').should('be.visible').and('contain', 'Leave this community?')
    cy.get('.community-leave-confirm-btn').click()

    cy.wait('@leaveCommunity').then(({ response }) => {
      expect(response.statusCode).to.eq(200)
      expect(response.body.status).to.eq('LEFT')
    })

    cy.url().should('include', '/me/profile', { timeout: 15000 })
    cy.get('.toast-success').should('contain', `You left ${communityName}`)
    cy.get('.community-header').should('not.exist')
  })
})
