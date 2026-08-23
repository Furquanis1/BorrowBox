// Global e2e support file — runs before every spec.
// Add custom commands and global configuration here.

// Disable uncaught exception failures from the application under test.
// BorrowBox may throw fetch errors when the backend is unavailable during
// landing page stat loading; these should not fail the test run.
Cypress.on('uncaught:exception', (err) => {
  // Returning false prevents Cypress from failing the test
  if (err.message.includes('Failed to fetch')) {
    return false
  }
  return true
})
