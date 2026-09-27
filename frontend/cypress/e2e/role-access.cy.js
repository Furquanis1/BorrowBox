describe('Role-based access & community header states (Slice 1)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }
  const throwawayPassword = 'TestPassword123!'

  let cseId
  let smgrEmail
  let pendingEmail
  let leftEmail
  let suspendedManagerId
  let pendingId
  let leftId

  const signInAs = (email, password) => {
    cy.clearCookies()
    cy.clearLocalStorage()
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').clear().type(email)
    cy.get('input[type="password"]').clear().type(password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/communities/', { timeout: 15000 })
  }

  beforeEach(() => {
    // Viewing the CSE home as a non-active member intentionally trips the
    // backend listings guard (Only active members...). The page still renders
    // its header + error EmptyState; the initial fetch rejection surfaces as an
    // uncaught promise rejection (useAsync sets state and rethrows). Tolerate
    // exactly this membership-guard message; any other uncaught error fails.
    cy.on('uncaught:exception', (error) => {
      const message = String(error?.message || '')
      return !message.includes('Only active members of this community can view its listings')
    })
  })

  before(() => {
    const stamp = `slice1_${Date.now()}`
    smgrEmail = `smgr-${stamp}@borrowbox.test`
    pendingEmail = `pending-${stamp}@borrowbox.test`
    leftEmail = `leftmember-${stamp}@borrowbox.test`

    cy.request('POST', '/api/auth/login', ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
    })

    // Manager whose membership is directly pinned to MANAGER/SUSPENDED. There
    // is no product API that creates a MANAGER role, so the SQL task is used
    // (see cypress.config.js); every state transition that follows is checked
    // against the app's own isManager(status === 'ACTIVE') semantics.
    cy.request('POST', '/api/auth/register', {
      fullName: `Suspended Manager ${stamp}`,
      email: smgrEmail,
      password: throwawayPassword,
    }).then((res) => {
      suspendedManagerId = res.body.user.id
    })
    cy.then(() => {
      cy.request('POST', '/api/auth/login', ahmed)
    })
    cy.then(() => {
      cy.task('ensureSuspendedManager', { userId: suspendedManagerId, communityId: cseId })
    })

    // Pending member: register then join CSE (MANAGER_APPROVAL admission).
    cy.request('POST', '/api/auth/register', {
      fullName: `Pending Member ${stamp}`,
      email: pendingEmail,
      password: throwawayPassword,
    }).then((res) => {
      pendingId = res.body.user.id
    })
    cy.then(() => {
      cy.request('POST', '/api/auth/login', { email: pendingEmail, password: throwawayPassword })
    })
    cy.then(() => {
      cy.request('POST', `/api/communities/${cseId}/join`, {})
    })

    // Left member: register, join, get approved by the CSE manager, then leave.
    cy.request('POST', '/api/auth/register', {
      fullName: `Left Member ${stamp}`,
      email: leftEmail,
      password: throwawayPassword,
    }).then((res) => {
      leftId = res.body.user.id
    })
    cy.then(() => {
      cy.request('POST', '/api/auth/login', { email: leftEmail, password: throwawayPassword })
    })
    cy.then(() => {
      cy.request('POST', `/api/communities/${cseId}/join`, {}).then((res) => {
        Cypress.env('leftMembershipId', res.body.id)
      })
    })
    cy.then(() => {
      cy.request('POST', '/api/auth/login', ahmed)
    })
    cy.then(() => {
      cy.request('POST', `/api/memberships/${Cypress.env('leftMembershipId')}/decision`, {
        decision: 'APPROVE',
      })
    })
    cy.then(() => {
      cy.request('POST', '/api/auth/login', { email: leftEmail, password: throwawayPassword })
    })
    cy.then(() => {
      cy.request('POST', `/api/communities/${cseId}/leave`, {})
    })
  })

  after(() => {
    const clean = (userId) => {
      if (!userId) return
      cy.task('restoreMembership', { userId, communityId: cseId })
    }
    cy.then(() => clean(suspendedManagerId))
    cy.then(() => clean(pendingId))
    cy.then(() => clean(leftId))
  })

  it('active manager sees Manager badge, manager tabs, and opens Dashboard + Flags', () => {
    signInAs(ahmed.email, ahmed.password)
    cy.visit(`/communities/${cseId}`)
    cy.get('.community-header-meta').should('contain', 'Manager')
    cy.get('.community-tab').should('contain', 'Dashboard')
    cy.get('.community-tab').should('contain', 'Flags')

    cy.visit(`/communities/${cseId}/dashboard`)
    cy.url().should('include', `/communities/${cseId}/dashboard`)
    cy.get('#overview-title').should('contain', 'Overview')
    cy.get('.stat-card').should('have.length.at.least', 6)

    cy.visit(`/communities/${cseId}/flags`)
    cy.url().should('include', `/communities/${cseId}/flags`)
    cy.get('#flags-title').should('contain', 'Flags')
    cy.get('.flags-page').should('contain', 'Open a flag')
  })

  it('non-manager member sees Member badge, base tabs, and is redirected from manager routes', () => {
    signInAs(salah.email, salah.password)
    cy.visit(`/communities/${cseId}`)
    cy.get('.community-header-meta').should('contain', 'Member')
    cy.get('.community-tab').should('have.length', 4)
    cy.get('.community-tab').should('not.contain', 'Dashboard')
    cy.get('.community-tab').should('not.contain', 'Flags')

    cy.visit(`/communities/${cseId}/dashboard`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}`)
    cy.url().should('not.include', '/dashboard')

    cy.visit(`/communities/${cseId}/flags`)
    cy.url().should('include', `/communities/${cseId}`)
    cy.url().should('not.include', '/flags')
  })

  it('suspended manager is treated as a non-manager for UI and routes', () => {
    signInAs(smgrEmail, throwawayPassword)
    cy.visit(`/communities/${cseId}`)
    cy.get('.community-header-meta').should('contain', 'Suspended')
    cy.get('.community-tab').should('have.length', 4)
    cy.get('.community-tab').should('not.contain', 'Dashboard')
    cy.get('.community-tab').should('not.contain', 'Flags')

    cy.visit(`/communities/${cseId}/dashboard`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}`)
    cy.url().should('not.include', '/dashboard')

    cy.visit(`/communities/${cseId}/flags`)
    cy.url().should('include', `/communities/${cseId}`)
    cy.url().should('not.include', '/flags')
  })

  it('pending member sees the Pending approval badge', () => {
    signInAs(pendingEmail, throwawayPassword)
    cy.visit(`/communities/${cseId}`)
    cy.get('.community-header-meta').should('contain', 'Pending approval')
    cy.get('.community-tab').should('have.length', 4)
  })

  it('left member sees the Left badge', () => {
    signInAs(leftEmail, throwawayPassword)
    cy.visit(`/communities/${cseId}`)
    cy.get('.community-header-meta').should('contain', 'Left')
    cy.get('.community-tab').should('have.length', 4)
  })
})
