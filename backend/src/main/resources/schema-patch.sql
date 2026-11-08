-- =============================================================================
-- schema-patch.sql  (V2.5.2 -- reservation expiry, V2.5.3 -- condition width,
--                    V2.6.2 -- notification idempotency keys)
-- =============================================================================
-- WHY THIS FILE EXISTS
--
-- schema.sql is executed by spring.sql.init on every application start, but
-- every statement in it is CREATE TABLE IF NOT EXISTS. Because of the IF NOT
-- EXISTS guard, an already-existing table is NEVER altered. This project also
-- uses spring.jpa.hibernate.ddl-auto=validate (ADR-018), so the new column must
-- physically exist in the database or Hibernate validation fails at startup.
--
-- The net effect on any database created before V2.5.2 and not dropped since:
-- schema.sql silently leaves the `transactions` table in its old shape, and the
-- application then fails fast on startup with an unknown-column validation
-- error. This file closes that gap.
--
-- HOW THIS PROJECT HANDLES SCHEMA CHANGES (ADR-018)
--
-- There is deliberately no migration tool (no Flyway, no Liquibase) and that is
-- not being changed here. The supported way to bring an existing local database
-- fully up to date remains the drop-and-recreate reset script:
--
--     .\scripts\reset-v2-db.ps1
--
-- which drops and recreates borrowbox_v2 so schema.sql rebuilds every table from
-- scratch. This patch file covers the case where you do NOT want to destroy
-- data. It is additive, idempotent, and runs on every startup immediately after
-- schema.sql (wired in application.properties via spring.sql.init.schema-locations).
-- It is a no-op on a fresh database, where schema.sql already created every
-- column and index.
--
-- WHY THE information_schema / PREPARE IDIOM
--
-- MySQL has NO `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` and no
-- `ALTER TABLE ... ADD INDEX IF NOT EXISTS`. Those are MariaDB extensions: the
-- first is rejected by plain MySQL 8.0 with a syntax error (verified against
-- 8.0.46), and the second does not exist in either. The portable equivalent is
-- to ask information_schema whether the object is already there and only then
-- run the DDL, which is what the SET/PREPARE/EXECUTE blocks below do. Each block
-- is a sequence of single statements separated by the statement terminator, so
-- Spring's script splitter executes them correctly.
--
-- Failure behaviour is deliberately loud: spring.sql.init.continue-on-error is
-- left at its default (false), so a broken patch aborts startup instead of
-- leaving the schema half-upgraded. The assertions below are the reason:
-- an existing database must never be assumed to already carry a new column.
--
-- =============================================================================

-- ---------------------------------------------------------------------------
-- V2.5.2 (1/2): persisted per-phase reservation expiry deadline.
--
-- Nullable by design. NULL means "no deadline has been set yet" and is never
-- treated as already expired, so rows written before this column existed stay
-- inert instead of instantly expiring after an upgrade.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE transactions ADD COLUMN reservation_expires_at DATETIME(6) DEFAULT NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'transactions'
      AND COLUMN_NAME = 'reservation_expires_at'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.5.2 (2/2): index backing the opportunistic expiry sweep query
-- (WHERE asset_id = ? AND state IN (...) AND reservation_expires_at < ?).
--
-- Purely a performance concern, so skipping it on a database that predates
-- V2.5.2 would still be correct -- but it is cheap to apply with the same
-- guard, so existing databases get it too.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE transactions ADD INDEX idx_transactions_asset_state (asset_id, state)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'transactions'
      AND INDEX_NAME = 'idx_transactions_asset_state'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.5.3 (1/1): widen asset_units.condition so the current-state projection of
-- the newest evidence metadata fits. conditionNote is capped at 1000
-- characters by TransactionService.CONDITION_NOTE_MAX_LENGTH, so a VARCHAR(255)
-- column could only store it by silently truncating -- which is explicitly not
-- allowed. A fresh database never sees this statement: schema.sql already
-- declares VARCHAR(1000).
--
-- The guard is on CHARACTER_MAXIMUM_LENGTH, not on mere column presence. A
-- database created before V2.5.3 already HAS the column, just at VARCHAR(255),
-- so a "does it exist" test would skip exactly the databases this block is
-- written for. Re-running after a successful widen is a no-op.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE asset_units MODIFY COLUMN `condition` VARCHAR(1000) DEFAULT NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'asset_units'
      AND COLUMN_NAME = 'condition'
      AND CHARACTER_MAXIMUM_LENGTH >= 1000
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.0: persisted authorization role.
--
-- The column is named user_role, not role: ROLE is a reserved word in MySQL
-- 8.0, so an unquoted `role` column would break both the DDL and Hibernate's
-- generated DML. The column is added with a NOT NULL 'USER' default so every
-- existing row is explicitly a regular user. Administrator access is never
-- granted automatically; it requires an out-of-band provisioning step.
-- schema.sql already carries the column on fresh databases, so this block is a
-- no-op there and only alters databases created before this version.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE users ADD COLUMN user_role VARCHAR(20) NOT NULL DEFAULT ''USER'' AFTER status',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'user_role'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.0: notifications table.
--
-- One row per notification sent to a user about a transaction event.
-- Nullable transaction_id allows future non-transaction notifications without
-- a schema change. Status is UNREAD / READ / DISMISSED / SUPPRESSED; read_at is
-- stamped when the recipient marks it read. The idempotency columns and their
-- constraints are added by the V2.6.2 block below, so this base table stays
-- compatible with databases created before those keys existed.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE TABLE notifications ('
        'id              BIGINT       NOT NULL AUTO_INCREMENT,'
        'recipient_id    BIGINT       NOT NULL,'
        'recipient_email VARCHAR(255) NOT NULL,'
        'transaction_id  BIGINT       DEFAULT NULL,'
        'type            VARCHAR(40)  NOT NULL,'
        'title           VARCHAR(255) NOT NULL,'
        'message         VARCHAR(2000) DEFAULT NULL,'
        'status          VARCHAR(20)  NOT NULL DEFAULT ''UNREAD'','
        'read_at         DATETIME(6)  DEFAULT NULL,'
        'created_at      DATETIME(6)  NOT NULL,'
        'updated_at      DATETIME(6)  NOT NULL,'
        'PRIMARY KEY (id),'
        'CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id),'
        'CONSTRAINT fk_notifications_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),'
        'INDEX idx_notifications_recipient_status (recipient_id, status),'
        'INDEX idx_notifications_recipient_created (recipient_id, created_at)'
        ') ENGINE=InnoDB',
        'SELECT 1'
    )
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.0: notification preferences table.
--
-- One row per (user, notification type) pair recording whether the user has
-- opted out of that type. Default is enabled (true).
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE TABLE notification_preferences ('
        'id            BIGINT       NOT NULL AUTO_INCREMENT,'
        'user_id       BIGINT       NOT NULL,'
        'type          VARCHAR(40)  NOT NULL,'
        'enabled       BOOLEAN      NOT NULL DEFAULT TRUE,'
        'created_at    DATETIME(6)  NOT NULL,'
        'updated_at    DATETIME(6)  NOT NULL,'
        'PRIMARY KEY (id),'
        'CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id),'
        'CONSTRAINT uq_notification_preferences_user_type UNIQUE (user_id, type)'
        ') ENGINE=InnoDB',
        'SELECT 1'
    )
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notification_preferences'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2: notification idempotency keys.
--
-- AUTOMATED HARD GUARDS
--
-- The blocks marked "HARD GUARD" execute during Spring SQL initialization.
-- spring.sql.init.continue-on-error is deliberately false, so a guard failure
-- aborts startup with an identifiable error. The failure SQL references a
-- deliberately nonexistent table whose name states the violated condition.
-- These guards never alter, truncate, rewrite, merge, or delete notification
-- rows. The status guard executes before any V2.6.2 DDL. The data guards execute
-- after the two nullable key columns are added but before any backfill or new
-- unique constraint. Adding those nullable columns is restart-safe and changes
-- no existing row.
--
-- The old (recipient_id, type, transaction_id) unique index is dropped last,
-- only after the new indexes and constraints have been established.
--
--
-- MANUAL READ-ONLY PREFLIGHT QUERIES
--
-- The queries below are documentation, not executable guards. The operator MUST
-- run them before deployment, record the baseline total/per-type counts, resolve
-- every incompatibility manually, and compare the same counts after migration.
-- Comments alone do not perform these checks.
--
--   -- Required tables, engines, and table types.
--   SELECT TABLE_NAME, TABLE_TYPE, ENGINE
--     FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA = DATABASE()
--      AND TABLE_NAME IN ('notifications', 'transaction_events');
--   -- Expect BASE TABLE for both and InnoDB for both.
--
--   -- Required notification columns and exact definitions.
--   SELECT COLUMN_NAME, COLUMN_TYPE, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH,
--          IS_NULLABLE, COLUMN_DEFAULT, EXTRA
--     FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = DATABASE()
--      AND TABLE_NAME = 'notifications'
--      AND COLUMN_NAME IN ('id', 'recipient_id', 'recipient_email',
--                          'transaction_id', 'source_event_id', 'dedupe_key',
--                          'type', 'status', 'read_at', 'created_at', 'updated_at')
--    ORDER BY ORDINAL_POSITION;
--   -- id/source_event_id/transaction_id must be signed BIGINT with matching
--   -- nullability rules. status must be VARCHAR, NOT NULL, DEFAULT 'UNREAD',
--   -- CHARACTER_MAXIMUM_LENGTH >= 10. source_event_id and dedupe_key are nullable.
--
--   -- Existing notification indexes and unique constraints.
--   SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
--     FROM information_schema.STATISTICS
--    WHERE TABLE_SCHEMA = DATABASE()
--      AND TABLE_NAME = 'notifications'
--    ORDER BY INDEX_NAME, SEQ_IN_INDEX;
--
--   -- Existing notification foreign keys.
--   SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME,
--          REFERENCED_COLUMN_NAME
--     FROM information_schema.KEY_COLUMN_USAGE
--    WHERE TABLE_SCHEMA = DATABASE()
--      AND TABLE_NAME = 'notifications'
--      AND REFERENCED_TABLE_NAME IS NOT NULL
--    ORDER BY CONSTRAINT_NAME, ORDINAL_POSITION;
--
--   -- Status values must all be in the application status set.
--   SELECT status, COUNT(*) AS row_count
--     FROM notifications
--    GROUP BY status
--    ORDER BY status;
--   SELECT COUNT(*) AS invalid_status_rows
--     FROM notifications
--    WHERE status IS NULL
--       OR status NOT IN ('UNREAD', 'READ', 'DISMISSED', 'SUPPRESSED');
--   -- Expect zero invalid_status_rows.
--
--   -- Any CHECK constraint referencing notifications.status requires manual
--   -- review. The automated guard fails closed and does not alter or drop it.
--   SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
--     FROM information_schema.TABLE_CONSTRAINTS tc
--     JOIN information_schema.CHECK_CONSTRAINTS cc
--       ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
--      AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
--    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
--      AND tc.TABLE_NAME = 'notifications'
--      AND tc.CONSTRAINT_TYPE = 'CHECK';
--
--   -- Baseline total and per-type counts. Record these before migration.
--   -- If source_event_id or dedupe_key is absent, record "column absent" for
--   -- that column and run only the key-specific queries below after the additive
--   -- migration has created the column. Do not backfill or edit rows manually.
--   SELECT COUNT(*) AS total_notifications FROM notifications;
--   SELECT type, COUNT(*) AS row_count,
--          SUM(source_event_id IS NULL) AS source_event_nulls,
--          SUM(source_event_id IS NOT NULL) AS source_event_values,
--          SUM(dedupe_key IS NULL) AS dedupe_key_nulls,
--          SUM(dedupe_key IS NOT NULL) AS dedupe_key_values
--     FROM notifications
--    GROUP BY type
--    WITH ROLLUP;
--
--   -- Prospective duplicate identities: must be zero.
--   -- Replace the CASE expression with the full prospective projection from
--   -- HARD GUARD (11/12), then group by recipient_id and prospective key.
--   -- Do not group only by recipient_id, type, and transaction_id: distinct
--   -- due dates are valid independent scheduled identities.
--
--   -- Scheduled rows with NULL transaction_id: must be zero.
--   SELECT id, recipient_id, type, status
--     FROM notifications
--    WHERE type IN ('LOAN_DUE_SOON', 'LOAN_OVERDUE')
--      AND transaction_id IS NULL;
--
--   -- Duplicate event identities: must be zero when source_event_id exists.
--   SELECT recipient_id, source_event_id, type, COUNT(*) AS duplicate_count
--     FROM notifications
--    WHERE source_event_id IS NOT NULL
--    GROUP BY recipient_id, source_event_id, type
--   HAVING COUNT(*) > 1;
--
--   -- Duplicate non-null dedupe keys: must be zero.
--   SELECT recipient_id, dedupe_key, COUNT(*) AS duplicate_count
--     FROM notifications
--    WHERE dedupe_key IS NOT NULL
--    GROUP BY recipient_id, dedupe_key
--   HAVING COUNT(*) > 1;
--
--   -- Inconsistent non-null dedupe keys: must be zero.
--   -- Valid forms are the exact old intermediate key, the exact legacy marker,
--   -- or a due-date key with a valid 26-character timestamp suffix.
--   SELECT id, recipient_id, type, transaction_id, source_event_id, dedupe_key
--     FROM notifications
--    WHERE dedupe_key IS NOT NULL
--      AND (type NOT IN ('LOAN_DUE_SOON', 'LOAN_OVERDUE')
--           OR transaction_id IS NULL
--           OR source_event_id IS NOT NULL
--           OR dedupe_key NOT LIKE CONCAT('scheduled:', type, ':', transaction_id, '%'));
--
--   -- Orphaned source_event_id values: must be zero.
--   SELECT n.id, n.recipient_id, n.source_event_id
--     FROM notifications n
--     LEFT JOIN transaction_events e ON e.id = n.source_event_id
--    WHERE n.source_event_id IS NOT NULL
--      AND e.id IS NULL;
--
--   -- Event/notification transaction mismatches: must be zero.
--   SELECT n.id, n.transaction_id, e.transaction_id AS event_transaction_id
--     FROM notifications n
--     JOIN transaction_events e ON e.id = n.source_event_id
--    WHERE n.source_event_id IS NOT NULL
--      AND (n.transaction_id IS NULL OR n.transaction_id <> e.transaction_id);
--
-- A fresh database already has every object from schema.sql, so the additive
-- blocks below are no-ops there.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (1/12): notifications.status is compatible.
--
-- SUPPRESSED is exactly 10 characters. Require a VARCHAR column of at least
-- width 10, NOT NULL, DEFAULT 'UNREAD', containing only application status
-- values. The guard aborts with the sentinel table name below. It deliberately
-- does NOT alter the column: widening, truncation, and data rewriting are
-- forbidden.
-- ---------------------------------------------------------------------------
SET @borrowbox_status_column_ok = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND COLUMN_NAME = 'status'
      AND DATA_TYPE = 'varchar'
      AND CHARACTER_MAXIMUM_LENGTH >= 10
      AND IS_NULLABLE = 'NO'
      AND COLUMN_DEFAULT = 'UNREAD'
);

SET @borrowbox_status_values_ok = 1;
SET @borrowbox_patch_ddl = IF(
    @borrowbox_status_column_ok = 1,
    'SET @borrowbox_status_values_ok = (SELECT COUNT(*) FROM notifications WHERE status IS NULL OR status NOT IN (''UNREAD'', ''READ'', ''DISMISSED'', ''SUPPRESSED''))',
    'SET @borrowbox_status_values_ok = 1'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

SET @borrowbox_patch_ddl = IF(
    @borrowbox_status_column_ok = 1
    AND @borrowbox_status_values_ok = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_notif_status_incompatible'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (2/12): existing status CHECK constraints require review.
--
-- Fail closed: if any CHECK constraint references notifications.status, the
-- migration aborts for manual review. It does not attempt to infer whether the
-- expression is restrictive and does not alter or drop the constraint.
-- ---------------------------------------------------------------------------
SET @borrowbox_status_check_count = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'notifications'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND LOWER(cc.CHECK_CLAUSE) LIKE '%status%'
);

SET @borrowbox_patch_ddl = IF(
    @borrowbox_status_check_count = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_notif_status_check_manual_review'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE notifications ADD COLUMN source_event_id BIGINT DEFAULT NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND COLUMN_NAME = 'source_event_id'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE notifications ADD COLUMN dedupe_key VARCHAR(255) DEFAULT NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND COLUMN_NAME = 'dedupe_key'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2: the old duplicate-scheduled guard is intentionally superseded by the
-- prospective identity collision guard below. Grouping only by
-- (recipient_id, type, transaction_id) rejects valid reminders for different
-- due dates, which are independent identities under the V2.6.2 model.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (3/12): scheduled notifications require a transaction.
-- A scheduled reminder with NULL transaction_id has no safe canonical identity.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_violations = (
    SELECT COUNT(*)
    FROM notifications
    WHERE type IN ('LOAN_DUE_SOON', 'LOAN_OVERDUE')
      AND transaction_id IS NULL
);
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_scheduled_null_transaction'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (4/12): duplicate non-null scheduled dedupe keys.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_violations = (
    SELECT COUNT(*)
    FROM (
        SELECT 1
        FROM notifications
        WHERE dedupe_key IS NOT NULL
        GROUP BY recipient_id, dedupe_key
        HAVING COUNT(*) > 1
    ) AS duplicate_dedupe
);
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_dup_dedupe_key'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (5/12): non-null scheduled keys use a known shape.
-- Due-date, legacy, and pre-release intermediate keys are all accepted here;
-- the exact format and prefix are validated by guard 10/12.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_violations = (
    SELECT COUNT(*)
    FROM notifications
    WHERE dedupe_key IS NOT NULL
      AND (type NOT IN ('LOAN_DUE_SOON', 'LOAN_OVERDUE')
           OR transaction_id IS NULL
           OR source_event_id IS NOT NULL
           OR NOT (BINARY dedupe_key = BINARY CONCAT('scheduled:', type, ':', transaction_id)
                   OR BINARY dedupe_key LIKE BINARY CONCAT('scheduled:', type, ':', transaction_id, ':%')))
);
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_bad_dedupe_key'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (6/12): duplicate event identities.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_violations = (
    SELECT COUNT(*)
    FROM (
        SELECT 1
        FROM notifications
        WHERE source_event_id IS NOT NULL
        GROUP BY recipient_id, source_event_id, type
        HAVING COUNT(*) > 1
    ) AS duplicate_event
);
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_dup_event_identity'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (7/12): source_event_id must reference transaction_events.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_violations = (
    SELECT COUNT(*)
    FROM notifications n
    LEFT JOIN transaction_events e ON e.id = n.source_event_id
    WHERE n.source_event_id IS NOT NULL
      AND e.id IS NULL
);
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_orphan_source_event'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (8/12): event and notification transactions must match.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_violations = (
    SELECT COUNT(*)
    FROM notifications n
    JOIN transaction_events e ON e.id = n.source_event_id
    WHERE n.source_event_id IS NOT NULL
      AND (n.transaction_id IS NULL
           OR n.transaction_id <> e.transaction_id)
);
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_event_txn_mismatch'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (9/12): scheduled notifications must not carry both
-- identity types.
--
-- A LOAN_DUE_SOON or LOAN_OVERDUE notification with source_event_id set but no
-- dedupe_key is ambiguous. Reject it for manual review rather than backfilling
-- a scheduled dedupe_key and creating a row with both identity types.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_violations = (
    SELECT COUNT(*)
    FROM notifications
    WHERE type IN ('LOAN_DUE_SOON', 'LOAN_OVERDUE')
      AND source_event_id IS NOT NULL
      AND dedupe_key IS NULL
);
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_scheduled_event_identity_conflict'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (10/12): existing scheduled keys have valid identities.
--
-- Accepted forms:
--   1. exact pre-release intermediate: scheduled:<TYPE>:<txnId>
--   2. exact legacy marker: scheduled:<TYPE>:<txnId>:legacy:<notificationId>
--   3. due-date key: scheduled:<TYPE>:<txnId>:yyyy-MM-dd'T'HH:mm:ss.SSSSSS
--
-- Due-date keys are validated for prefix, exact 26-character suffix length,
-- STR_TO_DATE success, and DATE_FORMAT round-trip. Existing due-date keys are
-- immutable here; they are not compared to the transaction's current due_at.
-- COALESCE ensures an unparseable suffix is a violation instead of a NULL
-- comparison that would silently exclude the row from this guard.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_violations = (
    SELECT COUNT(*)
    FROM notifications
    WHERE dedupe_key IS NOT NULL
      AND type IN ('LOAN_DUE_SOON', 'LOAN_OVERDUE')
      AND transaction_id IS NOT NULL
      AND source_event_id IS NULL
      AND NOT (
          BINARY dedupe_key = BINARY CONCAT('scheduled:', type, ':', transaction_id)
          OR BINARY dedupe_key = BINARY CONCAT(
              'scheduled:', type, ':', transaction_id, ':legacy:', id)
          OR (
              CHAR_LENGTH(dedupe_key) =
                  CHAR_LENGTH(CONCAT('scheduled:', type, ':', transaction_id, ':')) + 26
              AND BINARY LEFT(
                      dedupe_key,
                      CHAR_LENGTH(CONCAT('scheduled:', type, ':', transaction_id, ':')))
                  = BINARY CONCAT('scheduled:', type, ':', transaction_id, ':')
              AND COALESCE(
                      DATE_FORMAT(
                          STR_TO_DATE(RIGHT(dedupe_key, 26), '%Y-%m-%dT%H:%i:%s.%f'),
                          '%Y-%m-%dT%H:%i:%s.%f'),
                      '')
                  = RIGHT(dedupe_key, 26)
          )
      )
);
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_bad_scheduled_identity'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2 HARD GUARD (11/12): prospective final identities must be unique.
--
-- This is the identity-aware replacement for the retired guard 3. It includes
-- null-key candidates, exact old intermediate keys, existing due-date keys, and
-- existing legacy keys, and groups by (recipient_id, prospective key) only.
--
-- A due-date identity is safe only when BOTH are true:
--   - the due date is trustworthy (original_due_at rules); and
--   - no successful extension event exists at or after the notification was
--     created. Equal timestamps are conservative and produce a legacy marker.
-- This condition also applies when due_at = original_due_at.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_violations = (
    SELECT COUNT(*)
    FROM (
        SELECT projected.recipient_id, projected.prospective_dedupe_key
        FROM (
            SELECT
                n.recipient_id,
                CASE
                    WHEN n.dedupe_key IS NOT NULL
                         AND NOT (BINARY n.dedupe_key = BINARY CONCAT(
                             'scheduled:', n.type, ':', n.transaction_id))
                        THEN n.dedupe_key
                    WHEN t.due_at IS NOT NULL
                         AND t.original_due_at IS NOT NULL
                         AND NOT EXISTS (
                             SELECT 1
                             FROM transaction_events e
                             WHERE e.transaction_id = t.id
                               AND e.event_type IN (
                                   'EXTENSION_APPROVED',
                                   'EXTENSION_COUNTER_ACCEPTED')
                               AND e.created_at >= n.created_at
                         )
                         AND (
                             t.due_at = t.original_due_at
                             OR (
                                 t.due_at > t.original_due_at
                                 AND EXISTS (
                                     SELECT 1
                                     FROM transaction_events e
                                     WHERE e.transaction_id = t.id
                                       AND e.event_type IN (
                                           'EXTENSION_APPROVED',
                                           'EXTENSION_COUNTER_ACCEPTED')
                                       AND e.created_at < n.created_at
                                 )
                             )
                         )
                        THEN CONCAT(
                            'scheduled:', n.type, ':', n.transaction_id, ':',
                            DATE_FORMAT(t.due_at, '%Y-%m-%dT%H:%i:%s.%f'))
                    ELSE CONCAT(
                        'scheduled:', n.type, ':', n.transaction_id, ':legacy:', n.id)
                END AS prospective_dedupe_key
            FROM notifications n
            JOIN transactions t ON t.id = n.transaction_id
            WHERE n.type IN ('LOAN_DUE_SOON', 'LOAN_OVERDUE')
              AND n.transaction_id IS NOT NULL
              AND n.source_event_id IS NULL
        ) AS projected
        GROUP BY projected.recipient_id, projected.prospective_dedupe_key
        HAVING COUNT(*) > 1
    ) AS prospective_collisions
);
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_prospective_dedupe_collision'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.2 DML: normalize exact old intermediate keys, verify guard 12/12, backfill.
--
-- All validation above is read-only. These three DML steps share one explicit
-- transaction. If guard 12/12 finds an old intermediate key, the transaction is
-- rolled back before the sentinel error aborts initialization. DDL follows only
-- after the commit.
-- ---------------------------------------------------------------------------
START TRANSACTION;

UPDATE notifications
   SET dedupe_key = NULL
 WHERE type IN ('LOAN_DUE_SOON', 'LOAN_OVERDUE')
   AND transaction_id IS NOT NULL
   AND source_event_id IS NULL
   AND BINARY dedupe_key = BINARY CONCAT('scheduled:', type, ':', transaction_id);

-- V2.6.2 HARD GUARD (12/12): exact old intermediate keys must be gone after
-- normalization. Recheck the post-normalization rows so a failed UPDATE rolls
-- back instead of continuing with a non-canonical key.
SET @borrowbox_patch_violations = (
    SELECT COUNT(*)
    FROM notifications
    WHERE type IN ('LOAN_DUE_SOON', 'LOAN_OVERDUE')
      AND transaction_id IS NOT NULL
      AND source_event_id IS NULL
      AND BINARY dedupe_key = BINARY CONCAT('scheduled:', type, ':', transaction_id)
);
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'ROLLBACK'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;
SET @borrowbox_patch_ddl = IF(
    @borrowbox_patch_violations = 0,
    'SELECT 1',
    'SELECT 1 FROM bb_preflight_old_intermediate_key_remaining'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

UPDATE notifications n
   JOIN transactions t ON t.id = n.transaction_id
   SET n.dedupe_key = CASE
       WHEN t.due_at IS NOT NULL
            AND t.original_due_at IS NOT NULL
            AND NOT EXISTS (
                SELECT 1
                FROM transaction_events e
                WHERE e.transaction_id = t.id
                  AND e.event_type IN (
                      'EXTENSION_APPROVED',
                      'EXTENSION_COUNTER_ACCEPTED')
                  AND e.created_at >= n.created_at
            )
            AND (
                t.due_at = t.original_due_at
                OR (
                    t.due_at > t.original_due_at
                    AND EXISTS (
                        SELECT 1
                        FROM transaction_events e
                        WHERE e.transaction_id = t.id
                          AND e.event_type IN (
                              'EXTENSION_APPROVED',
                              'EXTENSION_COUNTER_ACCEPTED')
                          AND e.created_at < n.created_at
                    )
                )
            )
           THEN CONCAT(
               'scheduled:', n.type, ':', n.transaction_id, ':',
               DATE_FORMAT(t.due_at, '%Y-%m-%dT%H:%i:%s.%f'))
       ELSE CONCAT(
           'scheduled:', n.type, ':', n.transaction_id, ':legacy:', n.id)
   END
 WHERE n.dedupe_key IS NULL
   AND n.source_event_id IS NULL
   AND n.transaction_id IS NOT NULL
   AND n.type IN ('LOAN_DUE_SOON', 'LOAN_OVERDUE');

COMMIT;

SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE notifications ADD INDEX idx_notifications_source_event (source_event_id)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND INDEX_NAME = 'idx_notifications_source_event'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE notifications ADD CONSTRAINT fk_notifications_source_event FOREIGN KEY (source_event_id) REFERENCES transaction_events (id)',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND CONSTRAINT_NAME = 'fk_notifications_source_event'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE notifications ADD CONSTRAINT uq_notifications_recipient_source_event_type UNIQUE (recipient_id, source_event_id, type)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND INDEX_NAME = 'uq_notifications_recipient_source_event_type'
      AND NON_UNIQUE = 0
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE notifications ADD CONSTRAINT uq_notifications_recipient_dedupe UNIQUE (recipient_id, dedupe_key)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND INDEX_NAME = 'uq_notifications_recipient_dedupe'
      AND NON_UNIQUE = 0
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE transactions ADD INDEX idx_transactions_state_due_at (state, due_at)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'transactions'
      AND INDEX_NAME = 'idx_transactions_state_due_at'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE notifications DROP INDEX uq_notifications_user_type_transaction',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND INDEX_NAME = 'uq_notifications_user_type_transaction'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.0: webhook endpoints table.
--
-- One row per user-configured webhook endpoint. Each endpoint has a secret
-- token used to sign outgoing requests (HMAC-SHA256).
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE TABLE webhook_endpoints ('
        'id              BIGINT       NOT NULL AUTO_INCREMENT,'
        'user_id         BIGINT       NOT NULL,'
        'name            VARCHAR(255) NOT NULL,'
        'url             VARCHAR(2048) NOT NULL,'
        'secret_token    VARCHAR(255) NOT NULL,'
        'status          VARCHAR(20)  NOT NULL DEFAULT ''ACTIVE'','
        'created_at      DATETIME(6)  NOT NULL,'
        'updated_at      DATETIME(6)  NOT NULL,'
        'PRIMARY KEY (id),'
        'CONSTRAINT fk_webhook_endpoints_user FOREIGN KEY (user_id) REFERENCES users (id)'
        ') ENGINE=InnoDB',
        'SELECT 1'
    )
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'webhook_endpoints'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.0: webhook deliveries table.
--
-- One row per delivery attempt for outbound webhook notifications.
-- Tracks attempts, response status, and retry information.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE TABLE webhook_deliveries ('
        'id               BIGINT       NOT NULL AUTO_INCREMENT,'
        'endpoint_id      BIGINT       NOT NULL,'
        'event_id         VARCHAR(255) NOT NULL,'
        'event_type       VARCHAR(40)  NOT NULL,'
        'transaction_id   BIGINT       DEFAULT NULL,'
        'payload          TEXT         NULL,'
        'status           VARCHAR(20)  NOT NULL DEFAULT ''PENDING'','
        'http_status_code INT          DEFAULT NULL,'
        'response_body    VARCHAR(2000) DEFAULT NULL,'
        'error_message    VARCHAR(2000) DEFAULT NULL,'
        'attempt_number   INT          NOT NULL DEFAULT 1,'
        'next_retry_at    DATETIME(6)  DEFAULT NULL,'
        'claim_token      VARCHAR(64)  DEFAULT NULL,'
        'claimed_at       DATETIME(6)  DEFAULT NULL,'
        'claim_expires_at DATETIME(6)  DEFAULT NULL,'
        'created_at       DATETIME(6)  NOT NULL,'
        'updated_at       DATETIME(6)  NOT NULL,'
        'PRIMARY KEY (id),'
        'CONSTRAINT fk_webhook_deliveries_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints (id),'
        'CONSTRAINT uq_webhook_delivery_event_endpoint UNIQUE (event_id, endpoint_id),'
        'INDEX idx_webhook_deliveries_endpoint_status (endpoint_id, status),'
        'INDEX idx_webhook_deliveries_next_retry (next_retry_at),'
        'INDEX idx_webhook_deliveries_status_claim_expiry (status, claim_expires_at)'
        ') ENGINE=InnoDB',
        'SELECT 1'
    )
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'webhook_deliveries'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.1: store the outbound webhook payload on each delivery.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE webhook_deliveries ADD COLUMN payload TEXT NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'webhook_deliveries'
      AND COLUMN_NAME = 'payload'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.1: lease metadata for atomic webhook delivery claiming.
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE webhook_deliveries ADD COLUMN claim_token VARCHAR(64) NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'webhook_deliveries'
      AND COLUMN_NAME = 'claim_token'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE webhook_deliveries ADD COLUMN claimed_at DATETIME(6) NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'webhook_deliveries'
      AND COLUMN_NAME = 'claimed_at'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE webhook_deliveries ADD COLUMN claim_expires_at DATETIME(6) NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'webhook_deliveries'
      AND COLUMN_NAME = 'claim_expires_at'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE webhook_deliveries ADD INDEX idx_webhook_deliveries_status_claim_expiry (status, claim_expires_at)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'webhook_deliveries'
      AND INDEX_NAME = 'idx_webhook_deliveries_status_claim_expiry'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;

-- ---------------------------------------------------------------------------
-- V2.6.1: one webhook delivery per transaction event and endpoint.
--
-- The service checks before inserting, but the unique constraint is the
-- durable guard against duplicate records if the same event is processed
-- again (for example by a retried request or a concurrent worker).
-- ---------------------------------------------------------------------------
SET @borrowbox_patch_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE webhook_deliveries ADD CONSTRAINT uq_webhook_delivery_event_endpoint UNIQUE (event_id, endpoint_id)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'webhook_deliveries'
      AND INDEX_NAME = 'uq_webhook_delivery_event_endpoint'
);
PREPARE borrowbox_patch_stmt FROM @borrowbox_patch_ddl;
EXECUTE borrowbox_patch_stmt;
DEALLOCATE PREPARE borrowbox_patch_stmt;
