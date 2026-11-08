package com.borrowbox.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("borrowbox-legacy-test-db")
class NotificationMigrationPreflightIntegrationTest {

    private static final String EXPECTED_DATABASE = "borrowbox_legacy_test";
    private static final LocalDateTime FIXED_CREATED =
            LocalDateTime.of(2024, 2, 3, 4, 5, 6, 654321000);
    private static final LocalDateTime FIXED_DUE =
            LocalDateTime.of(2026, 2, 3, 4, 5, 6, 654321000);
    private static final DateTimeFormatter DUE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter DATETIME_LITERAL_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = legacyDataSource();
        resetLegacyDatabase();
        applySql("schema.sql");
        applySql("legacy-notifications-v2.6.0-downgrade.sql");
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void failedIntermediateNormalizationRollsBackAndKeepsOldIndex() {
        Scenario scenario = createScenario();
        long dueTransactionId = insertTransaction(scenario.communityId(), scenario.listingId(),
                scenario.assetId(), scenario.borrowerId(), scenario.lenderId(),
                "Due transaction", FIXED_DUE, FIXED_DUE);
        addKeyColumns();
        String oldIntermediateKey = "scheduled:LOAN_DUE_SOON:" + dueTransactionId;
        insertNotificationWithKeys(scenario, dueTransactionId,
                "LOAN_DUE_SOON", "Intermediate key", null, oldIntermediateKey);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_prevent_dedupe_normalization
                BEFORE UPDATE ON notifications
                FOR EACH ROW SET NEW.dedupe_key = OLD.dedupe_key
                """);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_old_intermediate_key_remaining");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(dedupeKey("Intermediate key")).isEqualTo(oldIntermediateKey);
        assertThat(indexCount("uq_notifications_user_type_transaction")).isEqualTo(1);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    @Test
    void varcharNineStatusAbortsBeforeAnyV262Ddl() {
        Scenario scenario = createScenario();
        jdbcTemplate.execute("""
                ALTER TABLE notifications
                MODIFY COLUMN status VARCHAR(9) NOT NULL DEFAULT 'UNREAD'
                """);
        insertLegacyNotification(scenario, "LOAN_APPROVED", "History", "UNREAD", null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_notif_status_incompatible");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(statusWidth()).isEqualTo(9);
        assertThat(columnCount("source_event_id")).isZero();
        assertThat(columnCount("dedupe_key")).isZero();
        assertThat(indexCount("uq_notifications_user_type_transaction")).isEqualTo(1);
    }

    @Test
    void varcharTenStatusIsAcceptedAndKeepsItsWidth() {
        Scenario scenario = createScenario();
        jdbcTemplate.execute("""
                ALTER TABLE notifications
                MODIFY COLUMN status VARCHAR(10) NOT NULL DEFAULT 'UNREAD'
                """);
        insertLegacyNotification(scenario, "LOAN_APPROVED", "Suppressed history", "SUPPRESSED", null);
        long dueSoonId = insertLegacyNotification(
                scenario, "LOAN_DUE_SOON", "Due soon", "UNREAD", null);
        List<Map<String, Object>> beforeRows = notificationRows();

        applySql("schema-patch.sql");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(statusWidth()).isEqualTo(10);
        assertThat(indexCount("uq_notifications_user_type_transaction")).isZero();
        assertThat(sourceEventIds()).containsOnlyNulls();
        assertThat(dedupeKey("Due soon")).isEqualTo(
                "scheduled:LOAN_DUE_SOON:" + scenario.transactionId() + ":legacy:" + dueSoonId);
        assertThat(dedupeKey("Suppressed history")).isNull();
    }

    @Test
    void enumStatusIsRejectedBeforeAnyV262Ddl() {
        Scenario scenario = createScenario();
        jdbcTemplate.execute("""
                ALTER TABLE notifications
                MODIFY COLUMN status ENUM('UNREAD', 'READ', 'DISMISSED', 'SUPPRESSED')
                NOT NULL DEFAULT 'UNREAD'
                """);
        insertLegacyNotification(scenario, "LOAN_APPROVED", "History", "UNREAD", null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_notif_status_incompatible");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(columnCount("source_event_id")).isZero();
        assertThat(columnCount("dedupe_key")).isZero();
        assertThat(indexCount("uq_notifications_user_type_transaction")).isEqualTo(1);
    }

    @Test
    void statusCheckIsRejectedForManualReviewBeforeAnyV262Ddl() {
        Scenario scenario = createScenario();
        jdbcTemplate.execute("""
                ALTER TABLE notifications
                ADD CONSTRAINT chk_notifications_status_rejects_suppressed
                CHECK (status IN ('UNREAD', 'READ', 'DISMISSED'))
                """);
        insertLegacyNotification(scenario, "LOAN_APPROVED", "History", "UNREAD", null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_notif_status_check_manual_review");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(columnCount("source_event_id")).isZero();
        assertThat(columnCount("dedupe_key")).isZero();
        assertThat(indexCount("uq_notifications_user_type_transaction")).isEqualTo(1);
    }

    @Test
    void nonRestrictiveStatusCheckIsRejectedForManualReviewBeforeAnyV262Ddl() {
        Scenario scenario = createScenario();
        jdbcTemplate.execute("""
                ALTER TABLE notifications
                ADD CONSTRAINT chk_notifications_status_requires_review
                CHECK (CHAR_LENGTH(status) >= 1)
                """);
        insertLegacyNotification(scenario, "LOAN_APPROVED", "History", "UNREAD", null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_notif_status_check_manual_review");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(columnCount("source_event_id")).isZero();
        assertThat(columnCount("dedupe_key")).isZero();
        assertThat(indexCount("uq_notifications_user_type_transaction")).isEqualTo(1);
    }

    @Test
    void unknownStatusValueAbortsBeforeAnyV262Ddl() {
        Scenario scenario = createScenario();
        insertLegacyNotification(scenario, "LOAN_APPROVED", "History", "BOGUS", null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_notif_status_incompatible");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(columnCount("source_event_id")).isZero();
        assertThat(columnCount("dedupe_key")).isZero();
        assertThat(indexCount("uq_notifications_user_type_transaction")).isEqualTo(1);
    }

    @Test
    void prospectiveCandidateCollisionAbortsBeforeBackfill() {
        Scenario scenario = createScenario();
        long dueTransactionId = insertTransaction(scenario.communityId(), scenario.listingId(),
                scenario.assetId(), scenario.borrowerId(), scenario.lenderId(),
                "Due transaction", FIXED_DUE, FIXED_DUE);
        addKeyColumns();
        jdbcTemplate.execute("""
                ALTER TABLE notifications DROP INDEX uq_notifications_user_type_transaction
                """);
        insertNotificationWithKeys(scenario, dueTransactionId,
                "LOAN_DUE_SOON", "Candidate one", null, null);
        insertNotificationWithKeys(scenario, dueTransactionId,
                "LOAN_DUE_SOON", "Candidate two", null, null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_prospective_dedupe_collision");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(notificationCount()).isEqualTo(2);
        assertThat(sourceEventIds()).containsOnlyNulls();
        assertThat(dedupeKeys()).containsOnlyNulls();
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    @Test
    void prospectiveCandidateAndExistingKeyCollisionAbortsBeforeBackfill() {
        Scenario scenario = createScenario();
        long dueTransactionId = insertTransaction(scenario.communityId(), scenario.listingId(),
                scenario.assetId(), scenario.borrowerId(), scenario.lenderId(),
                "Due transaction", FIXED_DUE, FIXED_DUE);
        String existingKey = scheduledDueKey("LOAN_DUE_SOON", dueTransactionId, FIXED_DUE);
        addKeyColumns();
        jdbcTemplate.execute("""
                ALTER TABLE notifications DROP INDEX uq_notifications_user_type_transaction
                """);
        insertNotificationWithKeys(scenario, dueTransactionId,
                "LOAN_DUE_SOON", "Existing key", null, existingKey);
        insertNotificationWithKeys(scenario, dueTransactionId,
                "LOAN_DUE_SOON", "Candidate", null, null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_prospective_dedupe_collision");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(notificationCount()).isEqualTo(2);
        assertThat(dedupeKey("Existing key")).isEqualTo(existingKey);
        assertThat(dedupeKey("Candidate")).isNull();
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    @Test
    void duplicateDedupeKeysAbortBeforeBackfill() {
        Scenario scenario = createScenario();
        addKeyColumns();
        String duplicateKey = "scheduled:LOAN_DUE_SOON:" + scenario.transactionId();
        insertNotificationWithKeys(scenario, "LOAN_DUE_SOON", "Duplicate key one", null, duplicateKey);
        insertNotificationWithKeys(scenario, "LOAN_OVERDUE", "Duplicate key two", null, duplicateKey);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_dup_dedupe_key");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(notificationCount()).isEqualTo(2);
        assertThat(dedupeKey("Duplicate key one")).isEqualTo(duplicateKey);
        assertThat(dedupeKey("Duplicate key two")).isEqualTo(duplicateKey);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    @Test
    void nonCanonicalDedupeKeyAbortsBeforeBackfill() {
        Scenario scenario = createScenario();
        addKeyColumns();
        String wrongKey = "scheduled:LOAN_OVERDUE:" + scenario.transactionId();
        insertNotificationWithKeys(scenario, "LOAN_DUE_SOON", "Wrong dedupe", null, wrongKey);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_bad_dedupe_key");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(dedupeKey("Wrong dedupe")).isEqualTo(wrongKey);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    @Test
    void invalidTimestampSuffixAbortsBeforeBackfill() {
        Scenario scenario = createScenario();
        addKeyColumns();
        String invalidKey = "scheduled:LOAN_DUE_SOON:" + scenario.transactionId()
                + ":2026-13-40T25:61:61.123456";
        insertNotificationWithKeys(scenario, "LOAN_DUE_SOON", "Invalid timestamp suffix",
                null, invalidKey);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_bad_scheduled_identity");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(notificationCount()).isEqualTo(1);
        assertThat(columnCount("source_event_id")).isEqualTo(1);
        assertThat(columnCount("dedupe_key")).isEqualTo(1);
        assertThat(sourceEventId("Invalid timestamp suffix")).isNull();
        assertThat(dedupeKey("Invalid timestamp suffix")).isEqualTo(invalidKey);
        assertThat(indexCount("uq_notifications_user_type_transaction")).isEqualTo(1);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    @Test
    void duplicateEventIdentitiesAbortBeforeBackfill() {
        Scenario scenario = createScenario();
        long eventId = insertEvent(scenario.transactionId(), "REQUEST_APPROVED",
                scenario.lenderId());
        addKeyColumns();
        jdbcTemplate.execute("""
                ALTER TABLE notifications DROP INDEX uq_notifications_user_type_transaction
                """);
        insertNotificationWithKeys(scenario, "LOAN_APPROVED", "Duplicate event one",
                eventId, null);
        insertNotificationWithKeys(scenario, "LOAN_APPROVED", "Duplicate event two",
                eventId, null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_dup_event_identity");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(notificationCount()).isEqualTo(2);
        assertThat(sourceEventId("Duplicate event one")).isEqualTo(eventId);
        assertThat(sourceEventId("Duplicate event two")).isEqualTo(eventId);
        assertThat(dedupeKey("Duplicate event one")).isNull();
        assertThat(dedupeKey("Duplicate event two")).isNull();
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    @Test
    void orphanedSourceEventAbortsBeforeBackfill() {
        Scenario scenario = createScenario();
        addKeyColumns();
        insertNotificationWithKeys(scenario, "LOAN_APPROVED", "Orphan event",
                999_999_999L, null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_orphan_source_event");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(sourceEventId("Orphan event")).isEqualTo(999_999_999L);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    @Test
    void eventTransactionMismatchAbortsBeforeBackfill() {
        Scenario scenario = createScenario();
        long otherTransactionId = insertTransaction(scenario, "Other transaction");
        long eventId = insertEvent(otherTransactionId, "REQUEST_APPROVED", scenario.lenderId());
        addKeyColumns();
        insertNotificationWithKeys(scenario, "LOAN_APPROVED", "Mismatched event",
                eventId, null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_event_txn_mismatch");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(sourceEventId("Mismatched event")).isEqualTo(eventId);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    @Test
    void scheduledNotificationWithNullTransactionAbortsBeforeBackfill() {
        Scenario scenario = createScenario();
        insertLegacyNotification(scenario, "LOAN_DUE_SOON", "Null transaction scheduled",
                "UNREAD", null, null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertPatchFails("bb_preflight_scheduled_null_transaction");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(notificationCount()).isEqualTo(1);
        assertThat(columnCount("source_event_id")).isEqualTo(1);
        assertThat(columnCount("dedupe_key")).isEqualTo(1);
        assertThat(sourceEventIds()).containsOnlyNulls();
        assertThat(dedupeKeys()).containsOnlyNulls();
        assertThat(indexCount("uq_notifications_user_type_transaction")).isEqualTo(1);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    @Test
    void scheduledNotificationWithEventIdentityAbortsBeforeBackfill() {
        Scenario scenario = createScenario();
        long eventId = insertEvent(scenario.transactionId(), "REQUEST_APPROVED", scenario.lenderId());
        addKeyColumns();
        insertNotificationWithKeys(scenario, "LOAN_DUE_SOON",
                "Scheduled event identity", eventId, null);
        List<Map<String, Object>> beforeRows = notificationRows();

        assertThat(sourceEventId("Scheduled event identity")).isEqualTo(eventId);
        assertThat(dedupeKey("Scheduled event identity")).isNull();

        assertPatchFails("bb_preflight_scheduled_event_identity_conflict");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(notificationCount()).isEqualTo(1);
        assertThat(sourceEventId("Scheduled event identity")).isEqualTo(eventId);
        assertThat(dedupeKey("Scheduled event identity")).isNull();
        assertThat(indexCount("uq_notifications_user_type_transaction")).isEqualTo(1);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isZero();
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isZero();
        assertThat(foreignKeyCount("fk_notifications_source_event")).isZero();
    }

    private DataSource legacyDataSource() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(requiredEnvironment("BORROWBOX_LEGACY_DATASOURCE_URL"));
        source.setUsername(requiredEnvironment("BORROWBOX_LEGACY_DATASOURCE_USERNAME"));
        source.setPassword(requiredEnvironment("BORROWBOX_LEGACY_DATASOURCE_PASSWORD"));
        return source;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required legacy test environment variable: " + name);
        }
        return value;
    }

    private void resetLegacyDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getCatalog();
            if (databaseName == null
                    || !EXPECTED_DATABASE.equals(databaseName.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("Refusing to reset database '"
                        + databaseName + "'; expected " + EXPECTED_DATABASE);
            }
            System.out.println("Resetting legacy test database: " + databaseName);

            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                List<String> tableNames = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery("""
                        SELECT TABLE_NAME
                          FROM information_schema.TABLES
                         WHERE TABLE_SCHEMA = DATABASE()
                           AND TABLE_TYPE = 'BASE TABLE'
                        """)) {
                    while (resultSet.next()) {
                        tableNames.add(resultSet.getString(1));
                    }
                }
                for (String tableName : tableNames) {
                    if (!tableName.matches("[A-Za-z0-9_]+")) {
                        throw new IllegalStateException("Unexpected table name: " + tableName);
                    }
                    statement.execute("DROP TABLE IF EXISTS `" + tableName + "`");
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not reset " + EXPECTED_DATABASE, exception);
        }
    }

    private void applySql(String resourcePath) {
        DatabasePopulatorUtils.execute(
                new ResourceDatabasePopulator(new ClassPathResource(resourcePath)), dataSource);
    }

    private void assertPatchFails(String sentinelTableName) {
        Throwable thrown = catchThrowable(() -> applySql("schema-patch.sql"));
        assertThat(thrown).isNotNull();
        StringBuilder messages = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        assertThat(messages.toString()).contains(sentinelTableName);
    }

    private Scenario createScenario() {
        long lenderId = insertUser("preflight.lender@example.com", "Preflight Lender");
        long borrowerId = insertUser("preflight.borrower@example.com", "Preflight Borrower");
        long communityId = insertCommunity(lenderId);
        long assetId = insertAsset(lenderId);
        long listingId = insertListing(assetId, communityId);
        long transactionId = insertTransaction(communityId, listingId, assetId,
                borrowerId, lenderId, "Primary transaction", null, null);
        return new Scenario(lenderId, borrowerId, communityId, assetId, listingId, transactionId);
    }

    private long insertUser(String email, String fullName) {
        jdbcTemplate.update("""
                INSERT INTO users (full_name, email, password_hash, status, user_role,
                                   created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', 'USER', ?, ?)
                """, fullName, email, "$2a$10$preflight.fixture.hash",
                timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertCommunity(long creatorId) {
        jdbcTemplate.update("""
                INSERT INTO communities (name, description, type, status, admission_mode,
                                         created_by, created_at, updated_at)
                VALUES ('Preflight Community', 'Preflight fixture', 'NEIGHBORHOOD',
                        'ACTIVE', 'MANAGER_APPROVAL', ?, ?, ?)
                """, creatorId, timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM communities WHERE name = 'Preflight Community'", Long.class);
    }

    private long insertAsset(long ownerId) {
        jdbcTemplate.update("""
                INSERT INTO assets (owner_id, title, description, status, created_at, updated_at)
                VALUES (?, 'Preflight Drill', 'Preflight fixture', 'ACTIVE', ?, ?)
                """, ownerId, timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM assets WHERE title = 'Preflight Drill'", Long.class);
    }

    private long insertListing(long assetId, long communityId) {
        jdbcTemplate.update("""
                INSERT INTO community_listings (asset_id, community_id, listing_status,
                                                listed_at, updated_at)
                VALUES (?, ?, 'LISTED', ?, ?)
                """, assetId, communityId, timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject("""
                SELECT id FROM community_listings WHERE asset_id = ? AND community_id = ?
                """, Long.class, assetId, communityId);
    }

    private long insertTransaction(Scenario scenario, String purpose) {
        return insertTransaction(scenario.communityId(), scenario.listingId(), scenario.assetId(),
                scenario.borrowerId(), scenario.lenderId(), purpose, null, null);
    }

    private long insertTransaction(long communityId, long listingId, long assetId,
                                   long borrowerId, long lenderId, String purpose,
                                   LocalDateTime dueAt, LocalDateTime originalDueAt) {
        jdbcTemplate.update("""
                INSERT INTO transactions (community_id, listing_id, asset_id, borrower_id,
                                          lender_id, state, purpose, requested_duration_days,
                                          due_at, original_due_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, 7, ?, ?, ?, ?)
                """, communityId, listingId, assetId, borrowerId, lenderId, purpose,
                dateTimeLiteral(dueAt), dateTimeLiteral(originalDueAt),
                timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM transactions WHERE purpose = ? ORDER BY id DESC LIMIT 1",
                Long.class, purpose);
    }

    private String scheduledDueKey(String type, long transactionId, LocalDateTime dueAt) {
        return "scheduled:" + type + ":" + transactionId + ":"
                + dueAt.truncatedTo(ChronoUnit.MICROS).format(DUE_FORMATTER);
    }

    private long insertEvent(long transactionId, String eventType, long actorId) {
        jdbcTemplate.update("""
                INSERT INTO transaction_events (transaction_id, event_type, actor_id, created_at)
                VALUES (?, ?, ?, ?)
                """, transactionId, eventType, actorId, timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject("""
                SELECT id FROM transaction_events
                 WHERE transaction_id = ? AND event_type = ?
                """, Long.class, transactionId, eventType);
    }

    private void addKeyColumns() {
        jdbcTemplate.execute(
                "ALTER TABLE notifications ADD COLUMN source_event_id BIGINT DEFAULT NULL");
        jdbcTemplate.execute(
                "ALTER TABLE notifications ADD COLUMN dedupe_key VARCHAR(255) DEFAULT NULL");
    }

    private long insertLegacyNotification(Scenario scenario, String type, String title,
                                          String status, LocalDateTime readAt) {
        return insertLegacyNotification(scenario, type, title, status, readAt,
                scenario.transactionId());
    }

    private long insertLegacyNotification(Scenario scenario, String type, String title,
                                          String status, LocalDateTime readAt,
                                          Long transactionId) {
        jdbcTemplate.update("""
                INSERT INTO notifications (recipient_id, recipient_email, transaction_id, type,
                                           title, message, status, read_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, scenario.borrowerId(), "preflight.recipient@example.com", transactionId,
                type, title, "Preflight message", status,
                readAt == null ? null : timestamp(readAt),
                timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE title = ?", Long.class, title);
    }

    private long insertNotificationWithKeys(Scenario scenario, String type, String title,
                                            Long sourceEventId, String dedupeKey) {
        return insertNotificationWithKeys(scenario, scenario.transactionId(),
                type, title, sourceEventId, dedupeKey);
    }

    private long insertNotificationWithKeys(Scenario scenario, long transactionId, String type,
                                            String title, Long sourceEventId, String dedupeKey) {
        jdbcTemplate.update("""
                INSERT INTO notifications (recipient_id, recipient_email, transaction_id,
                                           source_event_id, dedupe_key, type, title, message,
                                           status, read_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'UNREAD', NULL, ?, ?)
                """, scenario.borrowerId(), "preflight.recipient@example.com",
                transactionId, sourceEventId, dedupeKey, type, title,
                "Preflight message", timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE title = ?", Long.class, title);
    }

    private Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    private String dateTimeLiteral(LocalDateTime value) {
        return value == null ? null : value.format(DATETIME_LITERAL_FORMATTER);
    }

    private List<Map<String, Object>> notificationRows() {
        return jdbcTemplate.query("""
                SELECT id, recipient_id, recipient_email, transaction_id, type, title, message,
                       status, read_at, created_at, updated_at
                  FROM notifications
                 ORDER BY id
                """, (resultSet, rowNumber) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", resultSet.getLong("id"));
                    row.put("recipient_id", resultSet.getLong("recipient_id"));
                    row.put("recipient_email", resultSet.getString("recipient_email"));
                    row.put("transaction_id", resultSet.getObject("transaction_id"));
                    row.put("type", resultSet.getString("type"));
                    row.put("title", resultSet.getString("title"));
                    row.put("message", resultSet.getString("message"));
                    row.put("status", resultSet.getString("status"));
                    row.put("read_at", resultSet.getTimestamp("read_at"));
                    row.put("created_at", resultSet.getTimestamp("created_at"));
                    row.put("updated_at", resultSet.getTimestamp("updated_at"));
                    return row;
                });
    }

    private int notificationCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
    }

    private List<Object> sourceEventIds() {
        return jdbcTemplate.queryForList(
                "SELECT source_event_id FROM notifications ORDER BY id", Object.class);
    }

    private List<Object> dedupeKeys() {
        return jdbcTemplate.queryForList(
                "SELECT dedupe_key FROM notifications ORDER BY id", Object.class);
    }

    private Long sourceEventId(String title) {
        return jdbcTemplate.queryForObject(
                "SELECT source_event_id FROM notifications WHERE title = ?", Long.class, title);
    }

    private String dedupeKey(String title) {
        return jdbcTemplate.queryForObject(
                "SELECT dedupe_key FROM notifications WHERE title = ?", String.class, title);
    }

    private int statusWidth() {
        return jdbcTemplate.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH
                  FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'notifications'
                   AND COLUMN_NAME = 'status'
                """, Integer.class);
    }

    private int columnCount(String columnName) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'notifications'
                   AND COLUMN_NAME = ?
                """, Integer.class, columnName);
    }

    private int indexCount(String indexName) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT INDEX_NAME)
                  FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'notifications'
                   AND INDEX_NAME = ?
                """, Integer.class, indexName);
    }

    private int uniqueIndexCount(String indexName) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT INDEX_NAME)
                  FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'notifications'
                   AND INDEX_NAME = ?
                   AND NON_UNIQUE = 0
                """, Integer.class, indexName);
    }

    private int foreignKeyCount(String constraintName) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.TABLE_CONSTRAINTS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'notifications'
                   AND CONSTRAINT_NAME = ?
                   AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """, Integer.class, constraintName);
    }

    private record Scenario(long lenderId, long borrowerId, long communityId,
                            long assetId, long listingId, long transactionId) {
    }
}
