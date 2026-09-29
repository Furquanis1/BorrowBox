-- =============================================================================
-- schema-patch.sql  (V2.5.2 -- reservation expiry)
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
-- It is a no-op on a fresh database, where schema.sql already created both the
-- column and the index.
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
-- leaving the schema half-upgraded. The V2.5.2 assertions below are the reason:
-- an existing database must never be assumed to already carry the new column.
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
