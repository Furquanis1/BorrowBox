-- =============================================================================
-- schema-patch.sql  (V2.5.2 -- reservation expiry, V2.5.3 -- condition width)
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
-- a schema change. Status is UNREAD / READ / DISMISSED; read_at is stamped
-- when the recipient marks it read.
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
