-- BorrowBox V2.2.8 schema baseline (Community + Membership + Rules + Assets
-- + Transactions + Loan Lifecycle + Conversation + Loan Accountability Clock
-- + Loan Extensions + Return Disputes + Evidence + Queueing/Waitlist
-- + Transaction Events)
-- Fresh V2 database. V1 tables are not carried forward.
-- Matches exactly the entities mapped by the application:
--   users, communities, memberships, categories, community_rules,
--   assets, asset_units, community_listings, transactions,
--   transaction_messages, transaction_evidence, waitlist_entries,
--   transaction_events, transaction_event_deliveries

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS communities (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    name               VARCHAR(255)  NOT NULL,
    description        VARCHAR(1000) DEFAULT NULL,
    type               VARCHAR(30)   NOT NULL,
    status             VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    admission_mode     VARCHAR(30)   NOT NULL DEFAULT 'MANAGER_APPROVAL',
    created_by         BIGINT        NOT NULL,
    location_latitude  DECIMAL(10,8) DEFAULT NULL,
    location_longitude DECIMAL(11,8) DEFAULT NULL,
    location_radius_m  INT           DEFAULT NULL,
    active_name_key    VARCHAR(255)  DEFAULT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_communities_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT uq_creator_active_name UNIQUE (created_by, active_name_key)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS memberships (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    community_id        BIGINT       NOT NULL,
    role                VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    verification_method VARCHAR(30)  DEFAULT NULL,
    verified_at         DATETIME(6)  DEFAULT NULL,
    verified_by         BIGINT       DEFAULT NULL,
    joined_at           DATETIME(6)  DEFAULT NULL,
    context_metadata    JSON         DEFAULT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_memberships_community FOREIGN KEY (community_id) REFERENCES communities (id),
    CONSTRAINT fk_memberships_verified_by FOREIGN KEY (verified_by) REFERENCES users (id),
    CONSTRAINT uq_user_community UNIQUE (user_id, community_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS categories (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255)  NOT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS community_rules (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    community_id BIGINT       NOT NULL,
    rule_type    VARCHAR(40)  NOT NULL,
    value        JSON         DEFAULT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by   BIGINT       NOT NULL,
    updated_by   BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_rules_community  FOREIGN KEY (community_id) REFERENCES communities (id),
    CONSTRAINT fk_community_rules_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_community_rules_updated_by FOREIGN KEY (updated_by) REFERENCES users (id),
    INDEX idx_community_rules_community_type (community_id, rule_type)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS assets (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    owner_id    BIGINT        NOT NULL,
    title       VARCHAR(255)  NOT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    category_id BIGINT        DEFAULT NULL,
    status      VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_assets_owner     FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_assets_category  FOREIGN KEY (category_id) REFERENCES categories (id),
    INDEX idx_assets_owner_status (owner_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS asset_units (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    asset_id        BIGINT       NOT NULL,
    unit_identifier VARCHAR(255) DEFAULT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'AVAILABLE',
    `condition`     VARCHAR(255) DEFAULT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_asset_units_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    INDEX idx_asset_units_asset_status (asset_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS community_listings (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    asset_id       BIGINT       NOT NULL,
    community_id   BIGINT       NOT NULL,
    listing_status VARCHAR(30)  NOT NULL DEFAULT 'LISTED',
    listed_at      DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_listings_asset      FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT fk_community_listings_community  FOREIGN KEY (community_id) REFERENCES communities (id),
    CONSTRAINT uq_asset_community UNIQUE (asset_id, community_id),
    INDEX idx_community_listings_community_status (community_id, listing_status),
    INDEX idx_community_listings_asset_status (asset_id, listing_status)
) ENGINE=InnoDB;

-- V2.2.1+ transaction negotiation: one row per (listing, borrower, lender)
-- negotiation record. reserved_unit_id is NULL when no reservation is held;
-- MySQL permits many NULLs in a UNIQUE column, so at most one transaction can
-- hold a given non-null reserved_unit_id (the reservation authority is the
-- DB, with the pessimistic lock in TransactionService as the primary guard).
-- V2.2.2 adds started_at and completed_at for loan lifecycle tracking.
-- V2.2.4 adds the loan accountability clock: due_at and original_due_at are
-- stamped by the server at handover confirmation (due_at = started_at +
-- agreed_duration_days; original_due_at never changes), and
-- borrower_confirmed_at records the borrower's explicit receipt confirmation.
-- V2.2.5 adds single-extension negotiation fields (extension_requested_due_at,
-- extension_offered_due_at, extension_note, extension_requested_at), all
-- nullable; the fields hold the single pending negotiation and are cleared on
-- resolution so that the durable history lives in the conversation timeline.
-- V2.2.6 adds the return-dispute record (return_disputed_at /
-- return_disputed_by), stamped by the lender's "not received" action while
-- RETURN_DISPUTED. The disputed AssetUnit stays BORROWED.
CREATE TABLE IF NOT EXISTS transactions (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    community_id             BIGINT       NOT NULL,
    listing_id               BIGINT       NOT NULL,
    asset_id                 BIGINT       NOT NULL,
    borrower_id              BIGINT       NOT NULL,
    lender_id                BIGINT       NOT NULL,
    reserved_unit_id         BIGINT       DEFAULT NULL,
    state                    VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    purpose                  VARCHAR(255) NOT NULL,
    requested_duration_days  INT          NOT NULL,
    counter_purpose          VARCHAR(255) DEFAULT NULL,
    counter_duration_days    INT          DEFAULT NULL,
    counter_note             VARCHAR(255) DEFAULT NULL,
    counter_offered_at       DATETIME(6)  DEFAULT NULL,
    agreed_purpose           VARCHAR(255) DEFAULT NULL,
    agreed_duration_days     INT          DEFAULT NULL,
    agreed_at                DATETIME(6)  DEFAULT NULL,
    decided_at               DATETIME(6)  DEFAULT NULL,
    decided_by               BIGINT       DEFAULT NULL,
    decision_note            VARCHAR(255) DEFAULT NULL,
    reserved_at              DATETIME(6)  DEFAULT NULL,
    started_at               DATETIME(6)  DEFAULT NULL,
    due_at                   DATETIME(6)  DEFAULT NULL,
    original_due_at          DATETIME(6)  DEFAULT NULL,
    borrower_confirmed_at    DATETIME(6)  DEFAULT NULL,
    extension_requested_due_at DATETIME(6) DEFAULT NULL,
    extension_offered_due_at   DATETIME(6) DEFAULT NULL,
    extension_note             VARCHAR(255) DEFAULT NULL,
    extension_requested_at     DATETIME(6) DEFAULT NULL,
    return_disputed_at       DATETIME(6)  DEFAULT NULL,
    return_disputed_by       BIGINT       DEFAULT NULL,
    completed_at             DATETIME(6)  DEFAULT NULL,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_transactions_community     FOREIGN KEY (community_id) REFERENCES communities (id),
    CONSTRAINT fk_transactions_listing       FOREIGN KEY (listing_id) REFERENCES community_listings (id),
    CONSTRAINT fk_transactions_asset         FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT fk_transactions_borrower      FOREIGN KEY (borrower_id) REFERENCES users (id),
    CONSTRAINT fk_transactions_lender        FOREIGN KEY (lender_id) REFERENCES users (id),
    CONSTRAINT fk_transactions_reserved_unit FOREIGN KEY (reserved_unit_id) REFERENCES asset_units (id),
    CONSTRAINT fk_transactions_decided_by    FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT fk_transactions_return_disputed_by FOREIGN KEY (return_disputed_by) REFERENCES users (id),
    CONSTRAINT uq_transactions_reserved_unit UNIQUE (reserved_unit_id),
    INDEX idx_transactions_lender_state (lender_id, state),
    INDEX idx_transactions_borrower_state (borrower_id, state),
    INDEX idx_transactions_listing_state (listing_id, state)
) ENGINE=InnoDB;

-- V2.2.3 transaction conversation: one row per message in a transaction's
-- per-transaction conversation. USER rows (author_id set) carry borrower/lender
-- pickup-coordination and logistics messages. SYSTEM rows (author_id NULL) are
-- server-generated timeline entries emitted by lifecycle transitions and can
-- never be created through the public message API. ``kind`` defaults to USER.
-- Timestamps are always server-generated; client clocks are never trusted.
CREATE TABLE IF NOT EXISTS transaction_messages (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT        NOT NULL,
    author_id      BIGINT        DEFAULT NULL,
    kind           VARCHAR(10)   NOT NULL DEFAULT 'USER',
    body           VARCHAR(1000) NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_txn_messages_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_txn_messages_author      FOREIGN KEY (author_id)      REFERENCES users (id),
    INDEX idx_txn_messages_txn_created (transaction_id, created_at)
) ENGINE=InnoDB;

-- V2.2.6 transaction-scoped evidence: one row per uploaded evidence photo
-- during the return side of a loan. The binary is stored on the server media
-- directory; ``file_ref`` is a UUID-only reference never derived from user
-- input. ``captured_at`` is stamped by the backend clock at upload time;
-- client clocks are never trusted. Evidence is immutable (no update/delete).
CREATE TABLE IF NOT EXISTS transaction_evidence (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT         NOT NULL,
    type           VARCHAR(30)    NOT NULL,
    capturer_id    BIGINT         NOT NULL,
    file_ref       VARCHAR(255)   NOT NULL,
    content_type   VARCHAR(100)   NOT NULL,
    size_bytes     BIGINT         NOT NULL,
    captured_at    DATETIME(6)    NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_transaction_evidence_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_transaction_evidence_capturer    FOREIGN KEY (capturer_id)    REFERENCES users (id),
    INDEX idx_txn_evidence_txn_captured (transaction_id, captured_at)
) ENGINE=InnoDB;

-- V2.2.7 queueing/waitlist: one row per (asset, borrower) queue position for a
-- shared Asset across all communities that list it. The queue is per Asset and
-- entered through a specific CommunityListing; listing_id pins the community
-- the borrower queued in (community_id is derivable from listing_id and is NOT
-- duplicated). created_at is server-stamped at insert and, together with id, is
-- the authoritative queue order — client timestamps never determine position.
-- status is WAITING | PROMOTED | LEFT (V2.2.7):
--   WAITING   live and eligible for promotion.
--   PROMOTED  promotion succeeded: a normal PENDING transaction was created and
--             the entry is terminal audit (promoted_at stamped by the server).
--   LEFT      only when promotion skips an ineligible head (unlisted / archived
--             / no longer an active member / became owner); permanent, never
--             revived. A voluntary leave hard-deletes the WAITING row instead.
-- UNIQUE(asset_id, borrower_id) is the authoritative guard for one live
-- position per Asset.
CREATE TABLE IF NOT EXISTS waitlist_entries (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    asset_id                 BIGINT       NOT NULL,
    listing_id               BIGINT       NOT NULL,
    borrower_id              BIGINT       NOT NULL,
    purpose                  VARCHAR(255) NOT NULL,
    requested_duration_days  INT          NOT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    created_at               DATETIME(6)  NOT NULL,
    promoted_at              DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_waitlist_entries_asset     FOREIGN KEY (asset_id)     REFERENCES assets (id),
    CONSTRAINT fk_waitlist_entries_listing   FOREIGN KEY (listing_id)   REFERENCES community_listings (id),
    CONSTRAINT fk_waitlist_entries_borrower  FOREIGN KEY (borrower_id)  REFERENCES users (id),
    CONSTRAINT uq_waitlist_asset_borrower UNIQUE (asset_id, borrower_id),
    INDEX idx_waitlist_asset_status_created (asset_id, status, created_at),
    INDEX idx_waitlist_borrower_status (borrower_id, status)
) ENGINE=InnoDB;

-- V2.2.8 transaction events: structured semantic events with per-recipient delivery state.
-- Separate from transaction_messages (SYSTEM conversation history).
-- Created atomically in the same transaction as the underlying transaction state mutation.
-- actor_id is nullable for system-generated events (e.g., waitlist promotion).
-- payload is nullable JSON for structured event data (e.g., extension details).
CREATE TABLE IF NOT EXISTS transaction_events (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    transaction_id           BIGINT       NOT NULL,
    event_type               VARCHAR(40)  NOT NULL,
    actor_id                 BIGINT       DEFAULT NULL,
    payload                  JSON         DEFAULT NULL,
    created_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_txn_events_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_txn_events_actor       FOREIGN KEY (actor_id)       REFERENCES users (id),
    INDEX idx_txn_events_txn_created (transaction_id, created_at),
    INDEX idx_txn_events_type (event_type)
) ENGINE=InnoDB;

-- V2.2.8 per-recipient delivery of transaction events.
-- Tracks read/dismiss state per recipient.
-- UNIQUE(event_id, recipient_id) ensures exactly one delivery per recipient per event.
-- Status: UNREAD -> READ (open), UNREAD -> DISMISSED (later), DISMISSED -> READ (open from panel).
CREATE TABLE IF NOT EXISTS transaction_event_deliveries (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_id        BIGINT       NOT NULL,
    recipient_id    BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNREAD',
    read_at         DATETIME(6)  DEFAULT NULL,
    dismissed_at    DATETIME(6)  DEFAULT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_txn_event_deliveries_event     FOREIGN KEY (event_id)     REFERENCES transaction_events (id),
    CONSTRAINT fk_txn_event_deliveries_recipient FOREIGN KEY (recipient_id) REFERENCES users (id),
    CONSTRAINT uq_txn_event_delivery UNIQUE (event_id, recipient_id),
    INDEX idx_txn_event_deliveries_recipient_status (recipient_id, status)
) ENGINE=InnoDB;
