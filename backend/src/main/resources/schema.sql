-- BorrowBox V2.1.4 schema baseline (Community + Membership + Rules + Assets)
-- Fresh V2 database. V1 tables are not carried forward.
-- Matches exactly the entities mapped by the V2.1.4 application:
--   users, communities, memberships, categories, community_rules,
--   assets, asset_units

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
