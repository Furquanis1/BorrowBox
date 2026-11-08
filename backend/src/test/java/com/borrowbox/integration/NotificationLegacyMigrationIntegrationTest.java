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

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("borrowbox-legacy-test-db")
class NotificationLegacyMigrationIntegrationTest {

    private static final String EXPECTED_DATABASE = "borrowbox_legacy_test";
    private static final LocalDateTime FIXED_CREATED =
            LocalDateTime.of(2024, 1, 2, 3, 4, 5, 123456000);
    private static final LocalDateTime FIXED_DUE =
            LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123456000);
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
    void migratesLegacyRowsWithoutChangingValuesAndIsRerunnable() {
        long lenderId = insertUser("legacy.lender@example.com", "Legacy Lender");
        long borrowerId = insertUser("legacy.borrower@example.com", "Legacy Borrower");
        long communityId = insertCommunity(lenderId);
        long assetId = insertAsset(lenderId);
        long listingId = insertListing(assetId, communityId);
        long transactionId = insertTransaction(communityId, listingId, assetId, borrowerId, lenderId,
                FIXED_DUE, FIXED_DUE);

        long readId = insertNotification(borrowerId, "historical.read@example.com", transactionId,
                "LOAN_APPROVED", "Historical approved", "Original approval message", "READ",
                FIXED_CREATED.plusHours(1));
        long dueSoonId = insertNotification(borrowerId, "historical.due@example.com", transactionId,
                "LOAN_DUE_SOON", "Historical due soon", "Original due soon message", "UNREAD", null);
        long overdueId = insertNotification(borrowerId, "historical.overdue@example.com", transactionId,
                "LOAN_OVERDUE", "Historical overdue", "Original overdue message", "UNREAD", null);
        long nonScheduledId = insertNotification(borrowerId, "historical.non-scheduled@example.com",
                transactionId, "LOAN_STARTED", "Historical started", "Original started message",
                "UNREAD", null);

        assertThat(indexCount("uq_notifications_user_type_transaction")).isEqualTo(1);
        assertThat(columnCount("source_event_id")).isZero();
        assertThat(columnCount("dedupe_key")).isZero();

        List<Map<String, Object>> beforeRows = notificationRows();
        Map<String, Integer> beforeCounts = notificationCountsByType();
        int beforeStatusWidth = statusWidth();
        assertThat(beforeRows).hasSize(4);
        assertThat(beforeCounts).containsEntry("LOAN_DUE_SOON", 1)
                .containsEntry("LOAN_OVERDUE", 1);

        applySql("schema-patch.sql");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(notificationCountsByType()).isEqualTo(beforeCounts);
        assertThat(statusWidth()).isEqualTo(beforeStatusWidth);
        assertThat(indexCount("uq_notifications_user_type_transaction")).isZero();
        assertThat(indexCount("idx_notifications_source_event")).isEqualTo(1);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isEqualTo(1);
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isEqualTo(1);
        assertThat(foreignKeyCount("fk_notifications_source_event")).isEqualTo(1);

        assertThat(sourceEventIds()).containsOnlyNulls();
        assertThat(dedupeKey(readId)).isNull();
        assertThat(dedupeKey(nonScheduledId)).isNull();
        assertThat(dedupeKey(dueSoonId))
                .isEqualTo(scheduledDueKey("LOAN_DUE_SOON", transactionId, FIXED_DUE));
        assertThat(dedupeKey(overdueId))
                .isEqualTo(scheduledDueKey("LOAN_OVERDUE", transactionId, FIXED_DUE));

        List<Map<String, Object>> afterFirstPatch = notificationRowsWithKeys();
        applySql("schema-patch.sql");

        assertThat(notificationRows()).isEqualTo(beforeRows);
        assertThat(notificationCountsByType()).isEqualTo(beforeCounts);
        assertThat(notificationRowsWithKeys()).isEqualTo(afterFirstPatch);
        assertThat(statusWidth()).isEqualTo(beforeStatusWidth);
        assertThat(indexCount("uq_notifications_user_type_transaction")).isZero();
        assertThat(indexCount("idx_notifications_source_event")).isEqualTo(1);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isEqualTo(1);
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isEqualTo(1);
        assertThat(foreignKeyCount("fk_notifications_source_event")).isEqualTo(1);
    }

    @Test
    void historicalBackfillAppliesDueDatePolicyAndLegacyMarkers() {
        long lenderId = insertUser("policy.lender@example.com", "Policy Lender");
        long borrowerId = insertUser("policy.borrower@example.com", "Policy Borrower");
        long communityId = insertCommunity(lenderId);
        long assetId = insertAsset(lenderId);
        long listingId = insertListing(assetId, communityId);
        LocalDateTime dueAt = FIXED_DUE;
        LocalDateTime extendedDueAt = FIXED_DUE.plusDays(2);

        long trustworthyTxn = insertTransaction(communityId, listingId, assetId,
                borrowerId, lenderId, dueAt, dueAt);
        long equalEventTxn = insertTransaction(communityId, listingId, assetId,
                borrowerId, lenderId, dueAt, dueAt);
        long afterEventTxn = insertTransaction(communityId, listingId, assetId,
                borrowerId, lenderId, dueAt, dueAt);
        long beforeEventTxn = insertTransaction(communityId, listingId, assetId,
                borrowerId, lenderId, extendedDueAt, dueAt);
        long unextendedTxn = insertTransaction(communityId, listingId, assetId,
                borrowerId, lenderId, extendedDueAt, dueAt);
        long nullDueTxn = insertTransaction(communityId, listingId, assetId,
                borrowerId, lenderId, null, null);

        insertEvent(equalEventTxn, lenderId, "EXTENSION_APPROVED", FIXED_CREATED);
        insertEvent(afterEventTxn, lenderId, "EXTENSION_COUNTER_ACCEPTED",
                FIXED_CREATED.plusSeconds(1));
        insertEvent(beforeEventTxn, lenderId, "EXTENSION_APPROVED",
                FIXED_CREATED.minusSeconds(1));

        long trustworthyId = insertNotification(borrowerId, "policy.trustworthy@example.com",
                trustworthyTxn, "LOAN_DUE_SOON", "Trustworthy due date",
                "Trustworthy due date", "UNREAD", null);
        long equalEventId = insertNotification(borrowerId, "policy.equal@example.com",
                equalEventTxn, "LOAN_DUE_SOON", "Equal extension event",
                "Equal extension event", "UNREAD", null);
        long afterEventId = insertNotification(borrowerId, "policy.after@example.com",
                afterEventTxn, "LOAN_DUE_SOON", "After extension event",
                "After extension event", "UNREAD", null);
        long beforeEventId = insertNotification(borrowerId, "policy.before@example.com",
                beforeEventTxn, "LOAN_DUE_SOON", "Before extension event",
                "Before extension event", "UNREAD", null);
        long unextendedId = insertNotification(borrowerId, "policy.unextended@example.com",
                unextendedTxn, "LOAN_DUE_SOON", "Unextended due date",
                "Unextended due date", "UNREAD", null);
        long nullDueId = insertNotification(borrowerId, "policy.null@example.com",
                nullDueTxn, "LOAN_DUE_SOON", "Null due date",
                "Null due date", "UNREAD", null);

        applySql("schema-patch.sql");

        assertThat(dedupeKey(trustworthyId))
                .isEqualTo(scheduledDueKey("LOAN_DUE_SOON", trustworthyTxn, dueAt));
        assertThat(dedupeKey(equalEventId))
                .isEqualTo(scheduledLegacyKey("LOAN_DUE_SOON", equalEventTxn, equalEventId));
        assertThat(dedupeKey(afterEventId))
                .isEqualTo(scheduledLegacyKey("LOAN_DUE_SOON", afterEventTxn, afterEventId));
        assertThat(dedupeKey(beforeEventId))
                .isEqualTo(scheduledDueKey("LOAN_DUE_SOON", beforeEventTxn, extendedDueAt));
        assertThat(dedupeKey(unextendedId))
                .isEqualTo(scheduledLegacyKey("LOAN_DUE_SOON", unextendedTxn, unextendedId));
        assertThat(dedupeKey(nullDueId))
                .isEqualTo(scheduledLegacyKey("LOAN_DUE_SOON", nullDueTxn, nullDueId));
    }

    @Test
    void normalizesExactOldIntermediateKeyInPatchTransaction() {
        long lenderId = insertUser("normalize.lender@example.com", "Normalize Lender");
        long borrowerId = insertUser("normalize.borrower@example.com", "Normalize Borrower");
        long communityId = insertCommunity(lenderId);
        long assetId = insertAsset(lenderId);
        long listingId = insertListing(assetId, communityId);
        long transactionId = insertTransaction(communityId, listingId, assetId,
                borrowerId, lenderId, FIXED_DUE, FIXED_DUE);

        jdbcTemplate.execute(
                "ALTER TABLE notifications ADD COLUMN dedupe_key VARCHAR(255) DEFAULT NULL");
        long notificationId = insertNotification(borrowerId, "normalize.notification@example.com",
                transactionId, "LOAN_DUE_SOON", "Old intermediate key",
                "Old intermediate key", "UNREAD", null);
        jdbcTemplate.update("UPDATE notifications SET dedupe_key = ? WHERE id = ?",
                "scheduled:LOAN_DUE_SOON:" + transactionId, notificationId);

        applySql("schema-patch.sql");

        assertThat(dedupeKey(notificationId))
                .isEqualTo(scheduledDueKey("LOAN_DUE_SOON", transactionId, FIXED_DUE));
        assertThat(notificationRows()).hasSize(1);
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

    private long insertUser(String email, String fullName) {
        jdbcTemplate.update("""
                INSERT INTO users (full_name, email, password_hash, status, user_role,
                                   created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', 'USER', ?, ?)
                """, fullName, email, "$2a$10$legacy.fixture.hash",
                timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertCommunity(long creatorId) {
        jdbcTemplate.update("""
                INSERT INTO communities (name, description, type, status, admission_mode,
                                         created_by, created_at, updated_at)
                VALUES ('Legacy Community', 'Legacy migration fixture', 'NEIGHBORHOOD',
                        'ACTIVE', 'MANAGER_APPROVAL', ?, ?, ?)
                """, creatorId, timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM communities WHERE name = 'Legacy Community'", Long.class);
    }

    private long insertAsset(long ownerId) {
        jdbcTemplate.update("""
                INSERT INTO assets (owner_id, title, description, status, created_at, updated_at)
                VALUES (?, 'Legacy Drill', 'Legacy migration fixture', 'ACTIVE', ?, ?)
                """, ownerId, timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM assets WHERE title = 'Legacy Drill'", Long.class);
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

    private long insertTransaction(long communityId, long listingId, long assetId,
                                   long borrowerId, long lenderId,
                                   LocalDateTime dueAt, LocalDateTime originalDueAt) {
        jdbcTemplate.update("""
                INSERT INTO transactions (community_id, listing_id, asset_id, borrower_id,
                                          lender_id, state, purpose, requested_duration_days,
                                          due_at, original_due_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 'Legacy migration fixture', 7, ?, ?, ?, ?)
                """, communityId, listingId, assetId, borrowerId, lenderId,
                dateTimeLiteral(dueAt), dateTimeLiteral(originalDueAt),
                timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
        return jdbcTemplate.queryForObject("""
                SELECT id FROM transactions
                 WHERE listing_id = ? AND borrower_id = ? AND lender_id = ?
                 ORDER BY id DESC LIMIT 1
                """, Long.class, listingId, borrowerId, lenderId);
    }

    private long insertEvent(long transactionId, long actorId, String eventType,
                             LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO transaction_events (transaction_id, event_type, actor_id, created_at)
                VALUES (?, ?, ?, ?)
                """, transactionId, eventType, actorId, timestamp(createdAt));
        return jdbcTemplate.queryForObject("""
                SELECT id FROM transaction_events
                 WHERE transaction_id = ? AND event_type = ?
                 ORDER BY id DESC LIMIT 1
                """, Long.class, transactionId, eventType);
    }

    private String scheduledDueKey(String type, long transactionId, LocalDateTime dueAt) {
        return "scheduled:" + type + ":" + transactionId + ":"
                + dueAt.truncatedTo(ChronoUnit.MICROS).format(DUE_FORMATTER);
    }

    private String scheduledLegacyKey(String type, long transactionId, long notificationId) {
        return "scheduled:" + type + ":" + transactionId + ":legacy:" + notificationId;
    }

    private long insertNotification(long recipientId, String recipientEmail, long transactionId,
                                    String type, String title, String message, String status,
                                    LocalDateTime readAt) {
        jdbcTemplate.update("""
                INSERT INTO notifications (recipient_id, recipient_email, transaction_id, type,
                                           title, message, status, read_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, recipientId, recipientEmail, transactionId, type, title, message, status,
                readAt == null ? null : timestamp(readAt),
                timestamp(FIXED_CREATED), timestamp(FIXED_CREATED));
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
                    row.put("transaction_id", resultSet.getLong("transaction_id"));
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

    private List<Map<String, Object>> notificationRowsWithKeys() {
        return jdbcTemplate.query("""
                SELECT id, source_event_id, dedupe_key
                  FROM notifications
                 ORDER BY id
                """, (resultSet, rowNumber) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", resultSet.getLong("id"));
                    row.put("source_event_id", resultSet.getObject("source_event_id"));
                    row.put("dedupe_key", resultSet.getString("dedupe_key"));
                    return row;
                });
    }

    private Map<String, Integer> notificationCountsByType() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT type, COUNT(*) AS row_count
                  FROM notifications
                 GROUP BY type
                 ORDER BY type
                """, resultSet -> {
                    counts.put(resultSet.getString("type"), resultSet.getInt("row_count"));
                });
        return counts;
    }

    private List<Object> sourceEventIds() {
        return jdbcTemplate.queryForList(
                "SELECT source_event_id FROM notifications ORDER BY id", Object.class);
    }

    private String dedupeKey(long notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT dedupe_key FROM notifications WHERE id = ?", String.class, notificationId);
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
}
