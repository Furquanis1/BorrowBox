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
 * V2.5.3: returns the statement that puts every AssetUnit touched by the given
 * marker-scoped asset_unit_condition_history rows back to the condition it
 * carried before those rows were written (the previous_condition of the
 * earliest such row). Units with no matching history row are never listed, so a
 * cleanup that never projected a condition cannot clear a real one, and only
 * this run's rows are considered -- historical condition history is untouched.
 *
 * condition is a current-state projection of history: restoring the two
 * together is what keeps them from disagreeing after a marker is removed.
 */
function restoreConditionSql(historyScopeSql) {
  return [
    'UPDATE asset_units u',
    'JOIN (',
    '  SELECT asset_unit_id, MIN(id) AS first_id',
    '  FROM asset_unit_condition_history',
    `  WHERE ${historyScopeSql}`,
    '  GROUP BY asset_unit_id',
    ') m ON m.asset_unit_id = u.id',
    'JOIN asset_unit_condition_history h ON h.id = m.first_id',
    'SET u.condition = h.previous_condition;',
  ].join(' ')
}

/**
 * Marker-scoped history cleanup: restore each touched unit's condition, then
 * drop only the history rows this marker's transactions wrote. Must run before
 * those transactions (or their evidence) are deleted, otherwise the rows are
 * left orphaned by a restore that disables foreign key checks.
 */
function restoreConditionHistorySql(historyScopeSql) {
  return [
    restoreConditionSql(historyScopeSql),
    `DELETE FROM asset_unit_condition_history WHERE ${historyScopeSql};`,
  ].join(' ')
}

/**
 * Deletes a RETURN_DISPUTED marker transaction and releases its AssetUnit back
 * to AVAILABLE so the canonical seeded fixture survives for later specs.
 * RETURN_DISPUTED is terminal by design (unit stays BORROWED), so there is no
 * product API that can reverse it; the suite restores inventory directly.
 *
 * The loan reached RETURN_DISPUTED only after a confirmed handover, so the unit
 * carries a V2.5.3 condition projection and a history row keyed to this very
 * transaction. Both are restored here because the transaction is about to
 * disappear: a later marker-prefix sweep could no longer find them.
 */
function restoreReturnDispute(txnId) {
  const id = Number(txnId)
  if (!Number.isInteger(id) || id <= 0) throw new Error(`Invalid transaction id: ${txnId}`)
  const sql = [
    'SET FOREIGN_KEY_CHECKS=0;',
    restoreConditionHistorySql(`transaction_id = ${id}`),
    `DELETE FROM transaction_evidence WHERE transaction_id = ${id};`,
    `DELETE FROM transaction_messages WHERE transaction_id = ${id};`,
    `DELETE FROM transaction_event_deliveries WHERE event_id IN (SELECT id FROM transaction_events WHERE transaction_id = ${id});`,
    `DELETE FROM transaction_events WHERE transaction_id = ${id};`,
    `DELETE FROM reputation_events WHERE transaction_id = ${id};`,
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
    `DELETE FROM transaction_messages WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%');`,
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
/**
 * Intentionally wipes all transaction event rows so the events spec always
 * starts from an empty event database regardless of which specs ran before it.
 * Only the V2.2.8 event tables are touched. The V2.3.1 seeded transaction rows
 * are deliberately left in the database: the seed reconciles its own missing
 * lifecycle events and SYSTEM messages on the next run (without creating any
 * deliveries), so purging here does not leave the backend seed tests broken.
 */
function purgeEvents() {
  const sql = [
    'SET FOREIGN_KEY_CHECKS=0;',
    'DELETE FROM transaction_event_deliveries;',
    'DELETE FROM transaction_events;',
    'SET FOREIGN_KEY_CHECKS=1;',
  ].join(' ')
  execFileSync('mysql', dbArgs(sql))
  return true
}

function cleanupEventsDb(marker) {
  if (typeof marker !== 'string' || !/^[A-Za-z0-9_-]+$/.test(marker)) {
    throw new Error(`Invalid events marker: ${marker}`)
  }
  const markerTxns = `(SELECT id FROM transactions WHERE purpose LIKE '${marker}%')`
  const sql = [
    'SET FOREIGN_KEY_CHECKS=0;',
    // V2.5.3: undo this marker's condition projections and history rows before
    // the transactions they are keyed to are deleted below.
    restoreConditionHistorySql(`transaction_id IN ${markerTxns}`),
    `UPDATE asset_units SET status='AVAILABLE' WHERE id IN (SELECT reserved_unit_id FROM transactions WHERE purpose LIKE '${marker}%' AND reserved_unit_id IS NOT NULL);`,
    `DELETE FROM transaction_event_deliveries WHERE event_id IN (SELECT id FROM transaction_events WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%'));`,
    `DELETE FROM transaction_events WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%');`,
    `DELETE FROM transaction_evidence WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%');`,
    `DELETE FROM transaction_messages WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%');`,
    `DELETE FROM reputation_events WHERE transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%');`,
    `DELETE FROM transactions WHERE purpose LIKE '${marker}%';`,
    'SET FOREIGN_KEY_CHECKS=1;',
  ].join(' ')
  execFileSync('mysql', dbArgs(sql))
  return true
}

/**
 * V2.5.3: restores condition + asset_unit_condition_history for a marker whose
 * transactions the spec deliberately keeps (return-dispute drives them to a
 * terminal state and only restores inventory). Marker-prefix scoped like
 * restoreWaitlist / restoreFlags, so only this run's rows are touched and the
 * canonical fixture's units -- and any historical condition history -- are
 * left exactly as they were.
 */
function restoreConditionHistory(marker) {
  if (typeof marker !== 'string' || !/^[A-Za-z0-9_-]+$/.test(marker)) {
    throw new Error(`Invalid condition-history marker: ${marker}`)
  }
  const sql = restoreConditionHistorySql(
    `transaction_id IN (SELECT id FROM transactions WHERE purpose LIKE '${marker}%')`,
  )
  execFileSync('mysql', dbArgs(sql))
  return true
}

/**
 * Deletes V2.4.2 flags whose note carries the spec's marker prefix. Flags have
 * no product delete API, so the suite restores the canonical fixture directly.
 */
function restoreFlags(marker) {
  if (typeof marker !== 'string' || !/^[A-Za-z0-9_-]+$/.test(marker)) {
    throw new Error(`Invalid flags marker: ${marker}`)
  }
  const sql = [
    'SET FOREIGN_KEY_CHECKS=0;',
    `DELETE FROM flags WHERE note LIKE '${marker}%';`,
    'SET FOREIGN_KEY_CHECKS=1;',
  ].join(' ')
  execFileSync('mysql', dbArgs(sql))
  return true
}

/**
 * Removes a throwaway membership-review member and its membership row so the
 * canonical seeded fixture survives for later specs. The member is created and
 * moderated only inside membership-review.cy.js, which never leaves state.
 */
function restoreMembership({ userId, communityId }) {
  const uid = Number(userId)
  const cid = Number(communityId)
  if (!Number.isInteger(uid) || uid <= 0) throw new Error(`Invalid user id: ${userId}`)
  if (!Number.isInteger(cid) || cid <= 0) throw new Error(`Invalid community id: ${communityId}`)
  const sql = [
    'SET FOREIGN_KEY_CHECKS=0;',
    `DELETE FROM memberships WHERE user_id = ${uid} AND community_id = ${cid};`,
    `DELETE FROM users WHERE id = ${uid};`,
    'SET FOREIGN_KEY_CHECKS=1;',
  ].join(' ')
  execFileSync('mysql', dbArgs(sql))
  return true
}

/**
 * Pins a throwaway membership to the MANAGER/SUSPENDED state for the
 * role-access spec. There is no product API that can create a MANAGER role
 * (joining always creates MEMBER), so the spec seeds this one state directly
 * and removes it via restoreMembership afterwards; the canonical seed is
 * never mutated.
 */
function ensureSuspendedManager({ userId, communityId }) {
  const uid = Number(userId)
  const cid = Number(communityId)
  if (!Number.isInteger(uid) || uid <= 0) throw new Error(`Invalid user id: ${userId}`)
  if (!Number.isInteger(cid) || cid <= 0) throw new Error(`Invalid community id: ${communityId}`)
  const sql = [
    'INSERT INTO memberships (user_id, community_id, role, status, verification_method, created_at, updated_at)',
    `VALUES (${uid}, ${cid}, 'MANAGER', 'SUSPENDED', 'ADMIN', NOW(6), NOW(6))`,
    `ON DUPLICATE KEY UPDATE role='MANAGER', status='SUSPENDED', updated_at = NOW(6)`,
  ].join(' ')
  execFileSync('mysql', dbArgs(sql))
  return true
}

/**
 * Removes V2.4.2 rules a rules-pages spec created for one community + type so
 * the canonical fixture (no grace-period rules) survives for later specs.
 */
function restoreRules({ communityId, ruleType }) {
  const cid = Number(communityId)
  if (!Number.isInteger(cid) || cid <= 0) throw new Error(`Invalid community id: ${communityId}`)
  if (!/^[A-Z_]+$/.test(ruleType)) throw new Error(`Invalid rule type: ${ruleType}`)
  const sql = [
    'SET FOREIGN_KEY_CHECKS=0;',
    `DELETE FROM community_rules WHERE community_id = ${cid} AND rule_type = '${ruleType}';`,
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
        purgeEvents,
        restoreFlags,
        restoreConditionHistory,
        restoreMembership,
        restoreRules,
        ensureSuspendedManager,
      })
    },
  },
})