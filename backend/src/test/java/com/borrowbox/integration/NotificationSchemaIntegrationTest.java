package com.borrowbox.integration;

import com.borrowbox.entity.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class NotificationSchemaIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private int indexCount(String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'notifications'
                   AND INDEX_NAME = ?
                """, Integer.class, indexName);
        return count == null ? 0 : count;
    }

    private int uniqueIndexCount(String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'notifications'
                   AND INDEX_NAME = ?
                   AND NON_UNIQUE = 0
                """, Integer.class, indexName);
        return count == null ? 0 : count;
    }

    private String columnType(String tableName, String columnName) {
        return jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = ?
                   AND COLUMN_NAME = ?
                """, String.class, tableName, columnName);
    }

    private String nullable(String tableName, String columnName) {
        return jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = ?
                   AND COLUMN_NAME = ?
                """, String.class, tableName, columnName);
    }

    private String engine(String tableName) {
        return jdbcTemplate.queryForObject("""
                SELECT ENGINE FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = ?
                """, String.class, tableName);
    }

    private int transactionIndexCount(String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'transactions'
                   AND INDEX_NAME = ?
                """, Integer.class, indexName);
        return count == null ? 0 : count;
    }

    @Test
    void freshSchemaHasIdempotencyColumnsIndexesAndForeignKey() {
        assertThat(columnType("notifications", "source_event_id")).isEqualTo("bigint");
        assertThat(nullable("notifications", "source_event_id")).isEqualTo("YES");
        assertThat(columnType("notifications", "dedupe_key")).isEqualTo("varchar(255)");
        assertThat(nullable("notifications", "dedupe_key")).isEqualTo("YES");

        assertThat(columnType("notifications", "source_event_id"))
                .isEqualTo(columnType("transaction_events", "id"));

        assertThat(indexCount("idx_notifications_source_event")).isEqualTo(1);
        assertThat(uniqueIndexCount("uq_notifications_recipient_source_event_type")).isEqualTo(1);
        assertThat(uniqueIndexCount("uq_notifications_recipient_dedupe")).isEqualTo(1);
        assertThat(indexCount("uq_notifications_user_type_transaction")).isZero();

        Integer fkCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'notifications'
                   AND CONSTRAINT_NAME = 'fk_notifications_source_event'
                   AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """, Integer.class);
        assertThat(fkCount).isEqualTo(1);

        Integer statusWidth = jdbcTemplate.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'notifications'
                   AND COLUMN_NAME = 'status'
                """, Integer.class);
        assertThat(NotificationStatus.SUPPRESSED.name()).isEqualTo("SUPPRESSED");
        assertThat(NotificationStatus.SUPPRESSED.name()).hasSize(10);
        assertThat(statusWidth).isGreaterThanOrEqualTo(NotificationStatus.SUPPRESSED.name().length());

        assertThat(engine("notifications")).isEqualTo("InnoDB");
        assertThat(engine("transaction_events")).isEqualTo("InnoDB");
    }

    @Test
    void freshSchemaHasSchedulerDueAtIndex() {
        assertThat(transactionIndexCount("idx_transactions_state_due_at")).isEqualTo(1);
    }

    @Test
    void javaAndSqlFormatDueAtWithTheSameMicrosecondSuffix() {
        LocalDateTime dueAt = LocalDateTime.of(2026, 7, 8, 9, 10, 11, 123456000);
        String javaSuffix = dueAt.truncatedTo(ChronoUnit.MICROS)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"));

        String sqlSuffix = jdbcTemplate.queryForObject("""
                SELECT DATE_FORMAT(
                    CAST('2026-07-08 09:10:11.123456' AS DATETIME(6)),
                    '%Y-%m-%dT%H:%i:%s.%f')
                """, String.class);

        assertThat(sqlSuffix).isEqualTo(javaSuffix);
        assertThat(sqlSuffix).isEqualTo("2026-07-08T09:10:11.123456");
    }

    @Test
    void schemaPatchCanRunTwiceWithoutChangingRowCount() {
        Integer before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);

        DatabasePopulatorUtils.execute(
                new ResourceDatabasePopulator(new ClassPathResource("schema-patch.sql")), dataSource);
        DatabasePopulatorUtils.execute(
                new ResourceDatabasePopulator(new ClassPathResource("schema-patch.sql")), dataSource);

        Integer after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(after).isEqualTo(before);
    }
}
