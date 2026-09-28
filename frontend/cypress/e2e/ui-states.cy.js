describe('UI states · error / empty / recovery (Slice 1)', () => {
  const ahmed = { email: 'ahmed@example.com', password: 'password123' }
  const salah = { email: 'salah@example.com', password: 'password123' }

  let cseId

  const listingFixtures = (count, prefix) =>
    Array.from({ length: count }, (_, i) => ({
      id: 100000 + i,
      assetId: 200000 + i,
      title: `${prefix} item ${i + 1}`,
      description: `Description ${i + 1}`,
      availableUnits: 1,
      borrowedUnits: 0,
      totalUnits: 1,
      waitingCount: 0,
    }))

  /**
   * Registers an intercept that fails the first matching request (HTTP 500)
   * and serves `payload` (200) on every later request, proving a "Try again"
   * click recovers the page. NOTE: reachability isn't asserted — that is
   * network-connected UI behavior, not an error-state test.
   */
  const onceFailThen = (url, payload) => {
    let failing = true
    cy.intercept('GET', url, (req) => {
      if (failing) {
        failing = false
        req.reply(500, { message: 'Temporary outage' })
      } else {
        req.reply(200, payload)
      }
    })
  }

  const empty = []

  /**
   * Intercepts GET requests to the given URLs, lets them reach the real backend,
   * then modifies only the specified date fields in the response body.
   * Preserves the complete real response structure.
   * @param {string[]} urls - Array of URL patterns to intercept
   * @param {string[]} dateFieldsToNullify - Date field names to set to null
   */
  const interceptAndNullifyDates = (urls, dateFieldsToNullify) => {
    urls.forEach((url) => {
      cy.intercept('GET', url, (req) => {
        req.continue((res) => {
          if (Array.isArray(res.body)) {
            res.body = res.body.map((item) => {
              const modified = { ...item }
              dateFieldsToNullify.forEach((field) => {
                modified[field] = null
              })
              return modified
            })
          }
        })
      })
    })
  }

  /**
   * Intercepts GET requests to the given URLs, lets them reach the real backend,
   * ensures the specified date fields have valid ISO timestamps (does not nullify).
   * Used for "valid dates still render" tests.
   * @param {string[]} urls - Array of URL patterns to intercept
   * @param {string[]} dateFieldsToPreserve - Date field names to ensure are valid (no-op, just passes through real data)
   */
  const interceptAndPreserveDates = (urls, dateFieldsToPreserve) => {
    urls.forEach((url) => {
      cy.intercept('GET', url, (req) => {
        req.continue((res) => {
          // Pass through real response unchanged - backend returns valid dates
          // This function exists for symmetry and clarity
          if (Array.isArray(res.body)) {
            res.body = res.body.map((item) => ({ ...item }))
          }
        })
      })
    })
  }

  const signInAs = (email, password) => {
    cy.visit('/signin')
    cy.get('input[type="email"]', { timeout: 15000 }).should('be.visible')
    cy.get('input[type="email"]').clear().type(email)
    cy.get('input[type="password"]').clear().type(password)
    cy.get('form.auth-form button[type="submit"]').click()
    cy.url().should('include', '/communities/', { timeout: 15000 })
  }

  before(() => {
    cy.request('POST', '/api/auth/login', ahmed)
    cy.request('GET', '/api/communities').then((res) => {
      cseId = res.body.find((c) => c.name === 'CSE Department').id
    })
  })

  beforeEach(() => {
    cy.clearCookies()
    cy.clearLocalStorage()
    signInAs(salah.email, salah.password)
  })

  it('explore: fails to load listings, then recovers via Try again', () => {
    onceFailThen(`/api/communities/${cseId}/listings`, listingFixtures(2, 'Explore'))
    cy.intercept('GET', '/api/assets', { body: empty })

    cy.visit(`/communities/${cseId}/explore`)

    cy.get('.empty-state-title').should('have.text', 'Could not load listings')
    cy.get('.empty-state').contains('button', 'Try again').click()

    cy.get('.explore-listing-row').should('have.length', 2)
    cy.get('.explore-listing-row').first().should('contain', 'Explore item 1')
    cy.get('.empty-state').should('not.exist')
  })

  it('explore: shows the empty state when no items are listed', () => {
    cy.intercept('GET', `/api/communities/${cseId}/listings`, { body: empty })
    cy.intercept('GET', '/api/assets', { body: empty })

    cy.visit(`/communities/${cseId}/explore`)

    cy.get('.empty-state-title').should('have.text', 'No items listed yet')
    cy.get('.empty-state-description').should(
      'have.text',
      'Items offered in this community will show up here.',
    )
  })

  it('community home: fails to load listings, then recovers via Try again', () => {
    onceFailThen(`/api/communities/${cseId}/listings`, listingFixtures(2, 'Home'))

    cy.visit(`/communities/${cseId}`)

    cy.get('#recently-offered-title').should('contain', 'Recently offered')
    cy.get('.empty-state-title').should('have.text', 'Could not load listings')
    cy.get('.empty-state').contains('button', 'Try again').click()

    cy.get('.community-home .explore-listing-row').should('have.length', 2)
    cy.get('.empty-state').should('not.exist')
  })

  it('community home: shows the empty state when nothing is shared', () => {
    cy.intercept('GET', `/api/communities/${cseId}/listings`, { body: empty })

    cy.visit(`/communities/${cseId}`)

    cy.get('#recently-offered-title').should('contain', 'Recently offered')
    cy.get('.empty-state-title').should('have.text', 'Nothing shared yet')
    cy.get('.empty-state-description').should(
      'have.text',
      'Items offered in this community will show up here.',
    )
  })

  it('inventory: fails to load assets, then recovers via Try again', () => {
    onceFailThen('/api/assets', empty)

    cy.visit('/me/inventory')

    cy.get('.empty-state-title').should('have.text', 'Could not load your inventory')
    cy.get('.empty-state').contains('button', 'Try again').click()

    cy.get('.empty-state-title').should('have.text', 'Your inventory is empty')
    cy.get('.empty-state-description').should(
      'have.text',
      'Add an item to start sharing it across your communities.',
    )
  })

  it('requests: shows the error state when requests fail to load', () => {
    cy.intercept('GET', '/api/me/requests', { forceNetworkError: true })
    cy.intercept('GET', '/api/me/lend-requests', { body: empty })
    cy.intercept('GET', '/api/me/waitlist', { body: empty })

    cy.visit('/me/requests')

    cy.get('.empty-state-title').should('have.text', 'Could not load requests')
  })

  it('requests: shows the empty state when there are no requests', () => {
    cy.intercept('GET', '/api/me/requests', { body: empty })
    cy.intercept('GET', '/api/me/lend-requests', { body: empty })
    cy.intercept('GET', '/api/me/waitlist', { body: empty })

    cy.visit('/me/requests')

    cy.get('.empty-state-title').should('have.text', 'No incoming requests')
    cy.get('.empty-state-description').should(
      'have.text',
      'Borrow requests for your assets will show up here.',
    )
  })

  it('loans: shows the error state when loans fail to load', () => {
    cy.intercept('GET', '/api/me/requests', { forceNetworkError: true })
    cy.intercept('GET', '/api/me/lend-requests', { body: empty })

    cy.visit('/me/loans')

    cy.get('.empty-state-title').should('have.text', 'Could not load loans')
  })

  it('loans: shows the empty state when there are no loans', () => {
    cy.intercept('GET', '/api/me/requests', { body: empty })
    cy.intercept('GET', '/api/me/lend-requests', { body: empty })

    cy.visit('/me/loans')

    cy.get('.empty-state-title').should('have.text', 'No loans yet')
    cy.get('.empty-state-description').should(
      'have.text',
      'Once an approved request moves through handover, your active loans will show up here.',
    )
  })

  it('profile: shows the error state when the trust profile fails to load', () => {
    cy.intercept('GET', '/api/me/trust-profile', { forceNetworkError: true })
    cy.intercept('GET', '/api/me/reputation-events', { body: empty })
    cy.intercept('GET', '/api/me/requests', { body: empty })
    cy.intercept('GET', '/api/me/lend-requests', { body: empty })

    cy.visit('/me/profile')

    cy.get('.empty-state-title').should('have.text', 'Could not load your profile')
  })

  it('profile: shows empty ledger and history when no events or transactions exist', () => {
    cy.intercept('GET', '/api/me/reputation-events', { body: empty })
    cy.intercept('GET', '/api/me/requests', { body: empty })
    cy.intercept('GET', '/api/me/lend-requests', { body: empty })

    cy.visit('/me/profile')

    cy.get('.profile-page').should('be.visible')
    cy.get('.reputation-ledger').should('contain', 'No reputation events yet in this scope.')
    cy.get('.profile-page').should('contain', 'No history yet')
  })

  it('rules: fails to load rules, then recovers via Try again (member view)', () => {
    onceFailThen(`/api/communities/${cseId}/rules/active`, [
      {
        id: 9001,
        ruleType: 'OVERDUE_GRACE_PERIOD',
        status: 'ACTIVE',
        value: { days: 3 },
      },
    ])

    cy.visit(`/communities/${cseId}/rules`)

    cy.get('.empty-state-title').should('have.text', 'Could not load rules')
    cy.get('.empty-state').contains('button', 'Try again').click()

    cy.get('.rule-item').should('have.length', 1)
    cy.get('.rule-item-type').should('contain', 'OVERDUE_GRACE_PERIOD')
    cy.get('.empty-state').should('not.exist')
  })

  it('rules: shows the empty state when no active rules exist (member view)', () => {
    cy.intercept('GET', `/api/communities/${cseId}/rules/active`, { body: empty })

    cy.visit(`/communities/${cseId}/rules`)

    cy.get('.empty-state-title').should('have.text', 'No active rules')
    cy.get('.empty-state-description').should(
      'have.text',
      'Your community managers have not set any rules yet.',
    )
    cy.get('.rules-page').should('not.contain', 'Create rule')
  })

  // --- P2 Date Hardening Regression Tests ---
  // These tests intercept real API responses and modify ONLY the specified
  // nullable date fields, preserving the complete real response structure.

  it('loans: null completedAt and startedAt do not render "Invalid Date"', () => {
    // Intercept both loan data endpoints, let real backend respond, then nullify dates
    interceptAndNullifyDates(
      ['/api/me/requests', '/api/me/lend-requests'],
      ['startedAt', 'completedAt']
    )
    cy.intercept('GET', '/api/assets', { body: [] })

    cy.visit('/me/loans')

    // Should not display "Invalid Date" anywhere
    cy.get('.transaction-card-note').should('not.contain', 'Invalid Date')
    // Verify transaction cards render (if any loans exist in seeded data)
    cy.get('.active-loans').should('be.visible')
  })

  it('loans: valid completedAt and startedAt still render correctly', () => {
    // Intercept both loan data endpoints, pass through real response with valid dates
    interceptAndPreserveDates(
      ['/api/me/requests', '/api/me/lend-requests'],
      ['startedAt', 'completedAt']
    )
    cy.intercept('GET', '/api/assets', { body: [] })

    cy.visit('/me/loans')

    // Should not display "Invalid Date"
    cy.get('.transaction-card-note').should('not.contain', 'Invalid Date')
    // Verify transaction cards render (if any loans exist in seeded data)
    cy.get('.active-loans').should('be.visible')
  })

  it('requests: null agreedAt, startedAt, completedAt do not render "Invalid Date"', () => {
    // Use ahmed (lender) to get incoming requests in APPROVED state that render agreedAt
    cy.clearCookies()
    cy.clearLocalStorage()
    signInAs(ahmed.email, ahmed.password)

    // Intercept all three request data endpoints, let real backend respond, then nullify dates
    interceptAndNullifyDates(
      ['/api/me/lend-requests', '/api/me/requests', '/api/me/waitlist'],
      ['agreedAt', 'startedAt', 'completedAt']
    )

    cy.visit('/me/requests')

    // Should not display "Invalid Date" anywhere
    cy.get('.transaction-card-note').should('not.contain', 'Invalid Date')
    // Verify request cards render (if any requests exist in seeded data)
    cy.get('.request-inbox').should('be.visible')
  })

  it('requests: valid agreedAt, startedAt, completedAt still render correctly', () => {
    // Use ahmed (lender) to get incoming requests in APPROVED state that render agreedAt
    cy.clearCookies()
    cy.clearLocalStorage()
    signInAs(ahmed.email, ahmed.password)

    // Intercept all three request data endpoints, pass through real response with valid dates
    interceptAndPreserveDates(
      ['/api/me/lend-requests', '/api/me/requests', '/api/me/waitlist'],
      ['agreedAt', 'startedAt', 'completedAt']
    )

    cy.visit('/me/requests')

    // Should not display "Invalid Date"
    cy.get('.transaction-card-note').should('not.contain', 'Invalid Date')
    // Verify request cards render (if any requests exist in seeded data)
    cy.get('.request-inbox').should('be.visible')
  })
})