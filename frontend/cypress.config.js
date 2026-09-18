const { defineConfig } = require('cypress')
const { execFileSync } = require('child_process')

/**
 * DB connection for the suite's fixture-restoration task. Values are read from
 * the environment; credentials are never embedded in this source file. Missing
 * variables fail loudly instead of falling back to hardcoded values.
 */
function dbArgs(sql) {
  const required = [
    'CYPRESS_DB_HOST',
    'CYPRESS_DB_PORT',
    'CYPRESS_DB_NAME',
    'CYPRESS_DB_USER',
    'CYPRESS_DB_PASSWORD',
  ]
  const missing = required.filter((name) => !process.env[name])
  if (missing.length) {
    throw new Error(
      `Missing Cypress DB environment variable(s): ${missing.join(', ')}. ` +
        `Set ${required.join(', ')} before running the suite; credentials are ` +
        'never embedded in source.',
    )
  }
  return [
    `--host=${process.env.CYPRESS_DB_HOST}`,
    `--port=${process.env.CYPRESS_DB_PORT}`,
    `--user=${process.env.CYPRESS_DB_USER}`,
    `--password=${process.env.CYPRESS_DB_PASSWORD}`,
    process.env.CYPRESS_DB_NAME,
    `--execute=${sql}`,
  ]
}

/**
 * Deletes a RETURN_DISPUTED marker transaction and releases its AssetUnit back
 * to AVAILABLE so the canonical seeded fixture survives for later specs.
 * RETURN_DISPUTED is terminal by design (unit stays BORROWED), so there is no
 * product API that can reverse it; the suite restores inventory directly.
 */
function restoreReturnDispute(txnId) {
  const id = Number(txnId)
  if (!Number.isInteger(id) || id <= 0) throw new Error(`Invalid transaction id: ${txnId}`)
  const sql = [
    'SET FOREIGN_KEY_CHECKS=0;',
    `DELETE FROM transaction_evidence WHERE transaction_id = ${id};`,
    `DELETE FROM transaction_messages WHERE transaction_id = ${id};`,
    `UPDATE asset_units SET status='AVAILABLE' WHERE id = (SELECT reserved_unit_id FROM transactions WHERE id = ${id});`,
    `DELETE FROM transactions WHERE id = ${id};`,
    'SET FOREIGN_KEY_CHECKS=1;',
  ].join(' ')
  execFileSync('mysql', dbArgs(sql))
  return true
}

/**
 * Removes waitlist rows created by the waitlist spec so the canonical seeded
 * fixture (empty waitlists) survives for later specs. No product API deletes
 * arbitrary waitlist entries, so the suite cleans the table directly. The
 * marker must be a purpose prefix the spec guaranteed unique.
 */
function restoreWaitlist(marker) {
  if (typeof marker !== 'string' || !/^[A-Za-z0-9_-]+$/.test(marker)) {
    throw new Error(`Invalid waitlist marker: ${marker}`)
  }
  const sql = `DELETE FROM waitlist_entries WHERE purpose LIKE '${marker}%';`
  execFileSync('mysql', dbArgs(sql))
  return true
}

function restoreEvents(marker) {
  if (typeof marker !== 'string' || !/^[A-Za-z0-9_-]+$/.test(marker)) {
    throw new Error(`Invalid events marker: ${marker}`)
  }
  const sql = [
    'SET FOREIGN_KEY_CHECKS=0;',
    `UPDATE asset_units SET status='AVAILABLE' WHERE id IN (SELECT reserved_unit_id FROM transactions WHERE purpose LIKE '${marker}%' AND reserved_unit_id IS NOT NULL);`,
    `DELETE FROM transaction_event_deliveries WHERE event_id IN (SELECT id FROM transaction_events WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%'));`,
    `DELETE FROM transaction_events WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%');`,
    `DELETE FROM transactions WHERE purpose LIKE '${marker}%';`,
    'SET FOREIGN_KEY_CHECKS=1;',
  ].join(' ')
  execFileSync('mysql', dbArgs(sql))
  return true
}

/**
 * Comprehensive cleanup for events tests: cancels/rejects all marker transactions
 * and releases their units. Handles both lender and borrower perspectives.
 */
/**
 * Comprehensive cleanup task for events tests - simplified version
 */
function cleanupEventsDb(marker) {
  if (typeof marker !== 'string' || !/^[A-Za-z0-9_-]+$/.test(marker)) {
    throw new Error(`Invalid events marker: ${marker}`)
  }
  const sql = [
    'SET FOREIGN_KEY_CHECKS=0;',
    `UPDATE asset_units SET status='AVAILABLE' WHERE id IN (SELECT reserved_unit_id FROM transactions WHERE purpose LIKE '${marker}%' AND reserved_unit_id IS NOT NULL);`,
    `DELETE FROM transaction_event_deliveries WHERE event_id IN (SELECT id FROM transaction_events WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%'));`,
    `DELETE FROM transaction_events WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%');`,
    `DELETE FROM transaction_evidence WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%');`,
    `DELETE FROM transaction_messages WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%');`,
    `DELETE FROM transactions WHERE purpose LIKE '${marker}%';`,
    'SET FOREIGN_KEY_CHECKS=1;',
  ].join(' ')
  execFileSync('mysql', dbArgs(sql))
  return true
}

module.exports = defineConfig({
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL || 'http://localhost:3000',
    supportFile: 'cypress/support/e2e.js',
    specPattern: 'cypress/e2e/**/*.cy.{js,jsx}',
    video: false,
    screenshotOnRunFailure: true,
    defaultCommandTimeout: 10000,
    requestTimeout: 10000,
    responseTimeout: 10000,
    viewportWidth: 1280,
    viewportHeight: 720,
    setupNodeEvents(on) {
      on('task', {
        restoreReturnDispute,
        restoreWaitlist,
        restoreEvents,
        cleanupEventsDb,
      })
    },
  },
})