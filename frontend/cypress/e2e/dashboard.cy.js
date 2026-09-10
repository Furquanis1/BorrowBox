describe('Workspace Routing & Shell (Phase A + UX correction pass)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const zeroUser = {
    fullName: `Zero Shell ${Date.now()}`,
    email: `zero-shell-${Date.now()}@borrowbox.test`,
    password: 'TestPassword123!',
  }

  let ahmedName
  let cseId
  let hostelId
  let officeId

  before(() => {
    cy.request('POST', '/api/auth/login', {
      email: ahmed.email,
      password: ahmed.password,
    }).then((res) => {
      ahmedName = res.body.user.fullName
    })

    cy.request('POST', '/api/auth/register', zeroUser)
    cy.request('POST', '/api/auth/login', {
      email: ahmed.email,
      password: ahmed.password,
    })

    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
      hostelId = res.body.find((c) => c.name === 'Hostel Block B').id
      officeId = res.body.find((c) => c.name === 'Engineering Office').id
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

  it('should show the panel with community destinations, brand, and user name', () => {
    cy.get('.community-panel').should('be.visible')
    cy.get('.panel-brand').should('contain', 'BorrowBox')
    cy.get('.group-item').should('have.length.at.least', 3)
    cy.get('.community-header-title').should('be.visible')
    cy.contains(ahmedName).should('be.visible')
  })

  it('panel should contain only community destinations with no old sections', () => {
    cy.get('.community-panel').should('be.visible')
    cy.get('.community-panel').should('not.contain', 'Community Space')
    cy.get('.community-panel').should('not.contain', 'My Space')
    cy.get('.community-panel').should('not.contain', 'Requests')
    cy.get('.community-panel').should('not.contain', 'Loans')
    cy.get('.community-panel .group-item').should('have.length.at.least', 3)
  })

  it('should mark the active community tab with aria-current', () => {
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Home')

    cy.get('.community-tab').contains('Members').click()
    cy.url().should('include', '/members')
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Members')

    cy.get('.community-tab').contains('Home').click()
    cy.url().should('match', /\/communities\/\d+$/)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Home')
  })

  it('should have exactly one community-local navigation on Home', () => {
    cy.get('.community-home').should('be.visible')
    cy.get('.community-tabs').should('have.length', 1)
    cy.get('.community-tab').should('have.length', 4)
    cy.get('.community-page nav').should('have.length', 1)
    cy.get('.community-home-actions').should('not.exist')
  })

  it('should not render a desktop community dropdown', () => {
    cy.viewport(1280, 720)
    cy.visit(`/communities/${cseId}/explore`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/explore`)
    cy.get('.community-chip').should('not.exist')
    cy.get('.community-switcher').should('not.exist')
  })

  it('should show the quiet community identity meta in the header', () => {
    cy.visit(`/communities/${hostelId}`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${hostelId}`)
    cy.get('.community-header-title').should('contain', 'Hostel Block B')
    cy.get('.community-header-meta').invoke('text').should('match', /member/i)
  })

  it('should navigate My Space routes with no community header, switcher, or tabs', () => {
    cy.visit('/me/inventory')
    cy.url({ timeout: 15000 }).should('include', '/me/inventory')
    cy.get('.inventory-header h2').should('contain', 'My Inventory')
    cy.get('.community-header').should('not.exist')
    cy.get('.community-switcher').should('not.exist')
    cy.get('.community-tabs').should('not.exist')

    cy.visit('/me/requests')
    cy.url({ timeout: 15000 }).should('include', '/me/requests')
    cy.get('.request-inbox').should('be.visible')
    cy.get('.community-header').should('not.exist')

    cy.visit('/me/loans')
    cy.url({ timeout: 15000 }).should('include', '/me/loans')
    cy.get('.active-loans').should('be.visible')
    cy.get('.community-header').should('not.exist')
  })

  it('panel click from My Space should enter the community Home', () => {
    cy.visit('/me/inventory')
    cy.url({ timeout: 15000 }).should('include', '/me/inventory')

    cy.contains('.group-item', 'Hostel Block B').click()
    cy.url().should('include', `/communities/${hostelId}`)
    cy.url().should('match', /\/communities\/\d+$/)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Home')
  })

  it('panel click from Explore should enter the target community Home, not preserve sub-page', () => {
    cy.visit(`/communities/${cseId}/explore`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/explore`)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Explore')

    cy.contains('.group-item', 'Hostel Block B').click()
    cy.url().should('include', `/communities/${hostelId}`)
    cy.url().should('match', /\/communities\/\d+$/)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Home')
  })

  it('Inventory should stay global and be reachable from the UserBar inside a community', () => {
    cy.get('.userbar-nav').contains('a', 'Inventory').click()
    cy.url().should('include', '/me/inventory')
    cy.get('.inventory-header h2').should('contain', 'My Inventory')
    cy.get('.community-header').should('not.exist')
    cy.get('.community-tabs').should('not.exist')
  })

  it('Requests should be discoverable from the UserBar and stay global', () => {
    cy.get('.userbar-nav').should('contain', 'Requests')
    cy.get('.userbar-nav a[href="/me/requests"]').should('be.visible')

    cy.get('.userbar-nav').contains('a', 'Requests').click()
    cy.url().should('include', '/me/requests')
    cy.get('.request-inbox', { timeout: 15000 }).should('be.visible')
    cy.get('.userbar-nav a[href="/me/requests"]').should('have.class', 'active')
    cy.get('.community-header').should('not.exist')
    cy.get('.community-tabs').should('not.exist')

    cy.get('.userbar-nav').contains('a', 'Inventory').click()
    cy.url().should('include', '/me/inventory')
    cy.get('.inventory-header h2').should('contain', 'My Inventory')
    cy.get('.userbar-nav a[href="/me/inventory"]').should('have.class', 'active')
  })

  it('Loans should be discoverable from the UserBar and stay global', () => {
    cy.get('.userbar-nav a[href="/me/loans"]').should('be.visible')

    cy.get('.userbar-nav').contains('a', 'Loans').click()
    cy.url().should('include', '/me/loans')
    cy.get('.active-loans', { timeout: 15000 }).should('be.visible')
    cy.get('.userbar-nav a[href="/me/loans"]').should('have.class', 'active')
    cy.get('.community-header').should('not.exist')
    cy.get('.community-tabs').should('not.exist')
  })

  it('community pages should not expose My Inventory or duplicate nav', () => {
    cy.visit(`/communities/${cseId}`)
    cy.url({ timeout: 15000 }).should('match', /\/communities\/\d+$/)
    cy.get('.community-page').should('not.contain', 'My Inventory')
    cy.get('.community-page a[href="/me/inventory"]').should('not.exist')

    cy.get('.community-tab').contains('Explore').click()
    cy.url().should('include', `/communities/${cseId}/explore`)
    cy.get('.community-page').should('not.contain', 'My Inventory')
    cy.get('.community-page a[href="/me/inventory"]').should('not.exist')
  })

  it('Explore should only list offerings of the active community', () => {
    cy.visit(`/communities/${cseId}/explore`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/explore`)
    cy.request('GET', `/api/communities/${cseId}/listings`).then((res) => {
      cy.get('.explore-listing-row').should('have.length', res.body.length)
    })

    cy.contains('.group-item', 'Hostel Block B').click()
    cy.get('.community-tab').contains('Explore').click()
    cy.url().should('include', `/communities/${hostelId}/explore`)
    cy.get('.community-header-title').should('contain', 'Hostel Block B')
    cy.request('GET', `/api/communities/${hostelId}/listings`).then((res) => {
      cy.get('.explore-listing-row').should('have.length', res.body.length)
    })
  })

  it('CommunityTabs should navigate correctly', () => {
    cy.contains('.group-item', 'CSE Department').click()
    cy.url().should('include', `/communities/${cseId}`)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Home')
    cy.get('.community-home-section-title').should('contain', 'Recently offered')

    cy.get('.community-tab').contains('Explore').click()
    cy.url().should('include', `/communities/${cseId}/explore`)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Explore')

    cy.get('.community-tab').contains('Members').click()
    cy.url().should('include', `/communities/${cseId}/members`)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Members')
    cy.get('.members-page').should('be.visible')

    cy.get('.community-tab').contains('Rules').click()
    cy.url().should('include', `/communities/${cseId}/rules`)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Rules')
    cy.get('.rules-page').should('be.visible')
  })

  it('Inventory should be independent of community context', () => {
    cy.visit('/me/inventory')
    cy.url({ timeout: 15000 }).should('include', '/me/inventory')
    cy.get('.inventory-header h2').should('contain', 'My Inventory')
    cy.get('.community-header').should('not.exist')
    cy.get('.community-switcher').should('not.exist')
    cy.get('.community-tabs').should('not.exist')
  })

  it('Profile and Settings should be personal with no community chrome', () => {
    cy.visit('/me/profile')
    cy.url({ timeout: 15000 }).should('include', '/me/profile')
    cy.get('.profile-page').should('be.visible')
    cy.get('.community-header').should('not.exist')
    cy.get('.community-switcher').should('not.exist')
    cy.get('.community-tabs').should('not.exist')

    cy.visit('/me/settings')
    cy.url({ timeout: 15000 }).should('include', '/me/settings')
    cy.get('.settings-page').should('be.visible')
    cy.get('.community-header').should('not.exist')
    cy.get('.community-tabs').should('not.exist')
  })

  it('should keep legacy /dashboard/* redirect shims working', () => {
    cy.visit('/dashboard')
    cy.url({ timeout: 15000 }).should('match', /\/communities\/\d+$/)
    cy.get('.community-header-title').should('be.visible')

    cy.visit('/dashboard/explore')
    cy.url({ timeout: 15000 }).should('match', /\/communities\/\d+\/explore$/)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Explore')

    cy.visit('/dashboard/inventory')
    cy.url({ timeout: 15000 }).should('include', '/me/inventory')

    cy.visit('/dashboard/members')
    cy.url({ timeout: 15000 }).should('match', /\/communities\/\d+\/members$/)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Members')
  })

  it('should support deep-linking and refresh', () => {
    cy.visit(`/communities/${hostelId}/members`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${hostelId}/members`)
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Members')
    cy.get('.community-header-title').should('contain', 'Hostel Block B')
    cy.get('.community-header-meta').invoke('text').should('match', /member/i)

    cy.reload()
    cy.get('.community-header-title', { timeout: 15000 }).should('contain', 'Hostel Block B')
    cy.get('.community-tab[aria-current="page"]').should('contain', 'Members')
    cy.url().should('include', `/communities/${hostelId}/members`)

    cy.visit('/me/inventory')
    cy.url({ timeout: 15000 }).should('include', '/me/inventory')
    cy.get('.inventory-header h2').should('contain', 'My Inventory')
    cy.get('.community-header').should('not.exist')
  })

  it('should never let stale localStorage override an explicit community in the URL', () => {
    cy.visit('/me/inventory')
    cy.url({ timeout: 15000 }).should('include', '/me/inventory')

    cy.then(() => {
      localStorage.setItem('lastCommunityId', String(hostelId))
    })

    cy.visit(`/communities/${cseId}/explore`)
    cy.url({ timeout: 15000 }).should('include', `/communities/${cseId}/explore`)
    cy.get('.community-header-title').should('contain', 'CSE Department')
    cy.get('.community-chip').should('not.exist')
  })

  it('should show zero-community fallback for new users', () => {
    cy.clearCookies()
    cy.clearLocalStorage()
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').type(zeroUser.email)
    cy.get('input[type="password"]').type(zeroUser.password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/me/inventory', { timeout: 15000 })
    cy.get('.group-empty').should('contain', 'No communities yet.')
    cy.get('.community-switcher').should('not.exist')
    cy.get('.community-tabs').should('not.exist')
    cy.get('.community-header').should('not.exist')
  })

  it('should keep Requests and Loans in personal nav, outside the community panel', () => {
    cy.get('.bottom-nav').should('not.exist')
    cy.get('.community-panel').should('not.contain', 'Requests')
    cy.get('.community-panel').should('not.contain', 'Loans')
    cy.get('.userbar-nav').should('contain', 'Requests')
    cy.get('.userbar-nav').should('contain', 'Loans')
    cy.get('.userbar').should('not.contain', 'Notifications')
    cy.visit('/me/requests')
    cy.url({ timeout: 15000 }).should('include', '/me/requests')
    cy.get('.request-inbox').should('be.visible')
  })

  it('desktop shell should show the panel and hide mobile elements', () => {
    cy.viewport(1280, 720)
    cy.get('.community-panel').should('be.visible')
    cy.get('.userbar').should('be.visible')
    cy.get('.bottom-nav').should('not.exist')
    cy.get('.userbar-brand').should('not.be.visible')
  })

  it('mobile shell should hide the panel and show the userbar', () => {
    cy.viewport('iphone-x')
    cy.get('.community-panel').should('not.be.visible')
    cy.get('.userbar').should('be.visible')
    cy.get('.userbar-brand').should('be.visible')
    cy.get('.bottom-nav').should('not.exist')
  })

  it('skip link should move focus to main content', () => {
    cy.get('.skip-link').trigger('click', { force: true })
    cy.get('#main-content').should('have.focus')
  })

  it('should sign out via avatar menu and redirect to the landing page', () => {
    cy.get('.avatar-menu-btn').click()
    cy.get('.avatar-menu-dropdown').should('be.visible')
    cy.get('.avatar-menu-dropdown').contains('Sign Out').click()
    cy.location('pathname', { timeout: 15000 }).should((pathname) => {
      expect(['/', '/signin']).to.include(pathname)
    })
  })
})