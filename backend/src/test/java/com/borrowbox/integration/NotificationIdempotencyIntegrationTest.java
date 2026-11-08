package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.entity.Notification;
import com.borrowbox.entity.NotificationStatus;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.User;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.NotificationRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.service.NotificationPreferenceService;
import com.borrowbox.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V2.6.2 notification idempotency integration tests against the real schema.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class NotificationIdempotencyIntegrationTest {

    private static final LocalDateTime SCHEDULED_DUE_AT =
            LocalDateTime.of(2026, 3, 4, 5, 6, 7, 123456789);
    private static final DateTimeFormatter SCHEDULED_DUE_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    @Autowired private SeedDataInitializer seedDataInitializer;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionEventRepository eventRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationPreferenceService preferenceService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Transaction seededTransaction() {
        seedDataInitializer.seed();
        return transactionRepository.findAll().stream()
                .filter(txn -> txn.getBorrower() != null && txn.getLender() != null)
                .findFirst()
                .orElseThrow();
    }

    private TransactionEvent persistEvent(Transaction txn, TransactionEventType type) {
        TransactionEvent event = new TransactionEvent();
        event.setTransaction(txn);
        event.setEventType(type);
        event.setActor(txn.getLender());
        return eventRepository.saveAndFlush(event);
    }

    private int eventRowCount(Long recipientId, Long sourceEventId, NotificationType type) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notifications
                 WHERE recipient_id = ? AND source_event_id = ? AND type = ?
                """, Integer.class, recipientId, sourceEventId, type.name());
        return count == null ? 0 : count;
    }

    private String eventRowStatus(Long recipientId, Long sourceEventId, NotificationType type) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM notifications
                 WHERE recipient_id = ? AND source_event_id = ? AND type = ?
                """, String.class, recipientId, sourceEventId, type.name());
    }

    private int scheduledRowCount(Long recipientId, String dedupeKey) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notifications
                 WHERE recipient_id = ? AND dedupe_key = ?
                """, Integer.class, recipientId, dedupeKey);
        return count == null ? 0 : count;
    }

    private String scheduledDedupeKey(NotificationType type, Long transactionId,
                                      LocalDateTime dueAt) {
        return "scheduled:" + type.name() + ":" + transactionId + ":"
                + dueAt.truncatedTo(ChronoUnit.MICROS).format(SCHEDULED_DUE_AT_FORMATTER);
    }

    private User detachedRecipient(User original, String email) {
        User detached = new User();
        detached.setId(original.getId());
        detached.setFullName("Detached " + original.getFullName());
        detached.setEmail(email);
        detached.setPasswordHash("unused-detached-recipient");
        return detached;
    }

    private Map<String, Object> notificationSnapshot(Long notificationId) {
        notificationRepository.flush();
        return jdbcTemplate.queryForObject("""
                SELECT id, status, read_at, title, message, recipient_email, transaction_id,
                       source_event_id, dedupe_key, created_at, updated_at
                  FROM notifications
                 WHERE id = ?
                """, (resultSet, rowNumber) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", resultSet.getLong("id"));
                    row.put("status", resultSet.getString("status"));
                    row.put("read_at", resultSet.getTimestamp("read_at"));
                    row.put("title", resultSet.getString("title"));
                    row.put("message", resultSet.getString("message"));
                    row.put("recipient_email", resultSet.getString("recipient_email"));
                    row.put("transaction_id", resultSet.getObject("transaction_id"));
                    row.put("source_event_id", resultSet.getObject("source_event_id"));
                    row.put("dedupe_key", resultSet.getString("dedupe_key"));
                    row.put("created_at", resultSet.getTimestamp("created_at"));
                    row.put("updated_at", resultSet.getTimestamp("updated_at"));
                    return row;
                }, notificationId);
    }

    @Test
    void sameEventAndRecipientDoesNotDuplicate() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();
        TransactionEvent event = persistEvent(txn, TransactionEventType.REQUEST_APPROVED);

        Notification first = notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved").orElseThrow();
        Notification second = notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved").orElseThrow();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(eventRowCount(borrower.getId(), event.getId(), NotificationType.LOAN_APPROVED))
                .isEqualTo(1);
    }

    @Test
    void sameEventForTwoRecipientsCreatesOnePerRecipient() {
        Transaction txn = seededTransaction();
        TransactionEvent event = persistEvent(txn, TransactionEventType.LOAN_COMPLETED);

        Notification borrowerNotification = notificationService.createForEvent(event,
                txn.getBorrower(), NotificationType.LOAN_COMPLETED, "Complete", "Complete")
                .orElseThrow();
        Notification lenderNotification = notificationService.createForEvent(event,
                txn.getLender(), NotificationType.LOAN_COMPLETED, "Complete", "Complete")
                .orElseThrow();

        assertThat(lenderNotification.getId()).isNotEqualTo(borrowerNotification.getId());
        assertThat(eventRowCount(txn.getBorrower().getId(), event.getId(),
                NotificationType.LOAN_COMPLETED)).isEqualTo(1);
        assertThat(eventRowCount(txn.getLender().getId(), event.getId(),
                NotificationType.LOAN_COMPLETED)).isEqualTo(1);
    }

    @Test
    void sameTransactionTypeWithDifferentEventsCreatesTwo() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();
        TransactionEvent firstEvent = persistEvent(txn, TransactionEventType.REQUEST_APPROVED);
        TransactionEvent secondEvent = persistEvent(txn, TransactionEventType.REQUEST_APPROVED);

        Notification first = notificationService.createForEvent(firstEvent, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved").orElseThrow();
        Notification second = notificationService.createForEvent(secondEvent, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved").orElseThrow();

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(eventRowCount(borrower.getId(), firstEvent.getId(), NotificationType.LOAN_APPROVED))
                .isEqualTo(1);
        assertThat(eventRowCount(borrower.getId(), secondEvent.getId(), NotificationType.LOAN_APPROVED))
                .isEqualTo(1);
    }

    @Test
    void sameEventWithDifferentTypeCreatesTwo() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();
        TransactionEvent event = persistEvent(txn, TransactionEventType.REQUEST_APPROVED);

        Notification approved = notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved").orElseThrow();
        Notification rejected = notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_REJECTED, "Rejected", "Rejected").orElseThrow();

        assertThat(rejected.getId()).isNotEqualTo(approved.getId());
        assertThat(eventRowCount(borrower.getId(), event.getId(), NotificationType.LOAN_APPROVED))
                .isEqualTo(1);
        assertThat(eventRowCount(borrower.getId(), event.getId(), NotificationType.LOAN_REJECTED))
                .isEqualTo(1);
    }

    @Test
    void replayAfterMarkReadPreservesFirstReadTimestamp() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();
        TransactionEvent event = persistEvent(txn, TransactionEventType.REQUEST_APPROVED);

        Notification created = notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved").orElseThrow();
        Notification marked = notificationRepository.findById(
                notificationService.markAsRead(created.getId(), borrower).id()).orElseThrow();
        LocalDateTime firstReadAt = marked.getReadAt();

        Notification replayed = notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved").orElseThrow();

        assertThat(replayed.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(replayed.getReadAt()).isEqualTo(firstReadAt);
        assertThat(eventRowCount(borrower.getId(), event.getId(), NotificationType.LOAN_APPROVED))
                .isEqualTo(1);
    }

    @Test
    void eventReplayCannotOverwriteCanonicalDuplicateFields() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();
        TransactionEvent event = persistEvent(txn, TransactionEventType.REQUEST_APPROVED);

        Notification created = notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_APPROVED, "Original event title", "Original event message")
                .orElseThrow();
        notificationService.markAsRead(created.getId(), borrower);
        Map<String, Object> beforeReplay = notificationSnapshot(created.getId());

        User detachedRecipient = detachedRecipient(borrower, "detached.event@example.com");
        Notification replayed = notificationService.createForEvent(event, detachedRecipient,
                NotificationType.LOAN_APPROVED, "Changed event title", "Changed event message")
                .orElseThrow();

        assertThat(replayed.getId()).isEqualTo(created.getId());
        assertThat(notificationSnapshot(created.getId())).isEqualTo(beforeReplay);
        assertThat(beforeReplay.get("status")).isEqualTo(NotificationStatus.READ.name());
        assertThat(beforeReplay.get("read_at")).isNotNull();
        assertThat(beforeReplay.get("title")).isEqualTo("Original event title");
        assertThat(beforeReplay.get("message")).isEqualTo("Original event message");
        assertThat(beforeReplay.get("recipient_email")).isEqualTo(borrower.getEmail());
        assertThat(beforeReplay.get("transaction_id")).isEqualTo(txn.getId());
        assertThat(beforeReplay.get("source_event_id")).isEqualTo(event.getId());
        assertThat(eventRowCount(borrower.getId(), event.getId(), NotificationType.LOAN_APPROVED))
                .isEqualTo(1);
    }

    @Test
    void scheduledReplayCannotOverwriteCanonicalDuplicateFields() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();

        Notification created = notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), SCHEDULED_DUE_AT,
                "Original scheduled title", "Original scheduled message").orElseThrow();
        notificationService.markAsRead(created.getId(), borrower);
        Map<String, Object> beforeReplay = notificationSnapshot(created.getId());
        String dedupeKey = scheduledDedupeKey(
                NotificationType.LOAN_DUE_SOON, txn.getId(), SCHEDULED_DUE_AT);

        User detachedRecipient = detachedRecipient(borrower, "detached.scheduled@example.com");
        Notification replayed = notificationService.createScheduled(detachedRecipient,
                NotificationType.LOAN_DUE_SOON, txn.getId(), SCHEDULED_DUE_AT,
                "Changed scheduled title", "Changed scheduled message").orElseThrow();

        assertThat(replayed.getId()).isEqualTo(created.getId());
        assertThat(notificationSnapshot(created.getId())).isEqualTo(beforeReplay);
        assertThat(beforeReplay.get("status")).isEqualTo(NotificationStatus.READ.name());
        assertThat(beforeReplay.get("read_at")).isNotNull();
        assertThat(beforeReplay.get("title")).isEqualTo("Original scheduled title");
        assertThat(beforeReplay.get("message")).isEqualTo("Original scheduled message");
        assertThat(beforeReplay.get("recipient_email")).isEqualTo(borrower.getEmail());
        assertThat(beforeReplay.get("transaction_id")).isEqualTo(txn.getId());
        assertThat(beforeReplay.get("source_event_id")).isNull();
        assertThat(beforeReplay.get("dedupe_key")).isEqualTo(dedupeKey);
        assertThat(scheduledRowCount(borrower.getId(), dedupeKey)).isEqualTo(1);
    }

    @Test
    void optedOutEventCreatesSuppressedTombstoneThatReplayCannotRevive() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();
        long unreadBefore = notificationService.unreadCount(borrower);
        TransactionEvent event = persistEvent(txn, TransactionEventType.REQUEST_APPROVED);
        preferenceService.setPreference(borrower, NotificationType.LOAN_APPROVED, false);

        assertThat(notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved")).isEmpty();
        assertThat(eventRowStatus(borrower.getId(), event.getId(), NotificationType.LOAN_APPROVED))
                .isEqualTo(NotificationStatus.SUPPRESSED.name());
        assertThat(notificationService.listForUser(borrower))
                .noneMatch(n -> n.title().equals("Approved"));
        assertThat(notificationService.unreadCount(borrower)).isEqualTo(unreadBefore);

        Long suppressedId = jdbcTemplate.queryForObject("""
                SELECT id FROM notifications
                 WHERE recipient_id = ? AND source_event_id = ? AND type = ?
                """, Long.class, borrower.getId(), event.getId(), NotificationType.LOAN_APPROVED.name());
        assertThatThrownBy(() -> notificationService.markAsRead(suppressedId, borrower))
                .isInstanceOf(ResourceNotFoundException.class);

        preferenceService.setPreference(borrower, NotificationType.LOAN_APPROVED, true);
        assertThat(notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved")).isEmpty();

        assertThat(eventRowCount(borrower.getId(), event.getId(), NotificationType.LOAN_APPROVED))
                .isEqualTo(1);
        assertThat(eventRowStatus(borrower.getId(), event.getId(), NotificationType.LOAN_APPROVED))
                .isEqualTo(NotificationStatus.SUPPRESSED.name());
    }

    @Test
    void optingOutAfterVisibleNotificationDoesNotRetractIt() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();
        long unreadBefore = notificationService.unreadCount(borrower);
        TransactionEvent event = persistEvent(txn, TransactionEventType.REQUEST_APPROVED);

        notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved").orElseThrow();
        preferenceService.setPreference(borrower, NotificationType.LOAN_APPROVED, false);

        Notification replayed = notificationService.createForEvent(event, borrower,
                NotificationType.LOAN_APPROVED, "Approved", "Approved").orElseThrow();

        assertThat(replayed.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notificationService.listForUser(borrower))
                .anyMatch(n -> n.title().equals("Approved"));
        assertThat(notificationService.unreadCount(borrower)).isEqualTo(unreadBefore + 1);
        assertThat(eventRowCount(borrower.getId(), event.getId(), NotificationType.LOAN_APPROVED))
                .isEqualTo(1);
    }

    @Test
    void scheduledReminderIsIdempotentAndTypeScoped() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();

        Notification dueSoon = notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), SCHEDULED_DUE_AT,
                "Due soon", "Due soon").orElseThrow();
        Notification dueSoonReplay = notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), SCHEDULED_DUE_AT,
                "Due soon", "Due soon").orElseThrow();
        Notification overdue = notificationService.createScheduled(borrower,
                NotificationType.LOAN_OVERDUE, txn.getId(), SCHEDULED_DUE_AT,
                "Overdue", "Overdue").orElseThrow();

        assertThat(dueSoonReplay.getId()).isEqualTo(dueSoon.getId());
        assertThat(overdue.getId()).isNotEqualTo(dueSoon.getId());
        assertThat(scheduledRowCount(borrower.getId(),
                scheduledDedupeKey(NotificationType.LOAN_DUE_SOON, txn.getId(),
                        SCHEDULED_DUE_AT))).isEqualTo(1);
        assertThat(scheduledRowCount(borrower.getId(),
                scheduledDedupeKey(NotificationType.LOAN_OVERDUE, txn.getId(),
                        SCHEDULED_DUE_AT))).isEqualTo(1);
    }

    @Test
    void changedDueAtCreatesANewScheduledReminder() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();
        LocalDateTime firstDueAt = SCHEDULED_DUE_AT;
        LocalDateTime secondDueAt = SCHEDULED_DUE_AT.plusDays(2);

        Notification first = notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), firstDueAt,
                "First due date", "First due date").orElseThrow();
        Notification second = notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), secondDueAt,
                "Second due date", "Second due date").orElseThrow();

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(scheduledRowCount(borrower.getId(),
                scheduledDedupeKey(NotificationType.LOAN_DUE_SOON, txn.getId(),
                        firstDueAt))).isEqualTo(1);
        assertThat(scheduledRowCount(borrower.getId(),
                scheduledDedupeKey(NotificationType.LOAN_DUE_SOON, txn.getId(),
                        secondDueAt))).isEqualTo(1);
    }

    @Test
    void scheduledCreationTruncatesDueAtToMicroseconds() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();
        LocalDateTime dueAt = LocalDateTime.of(2026, 5, 6, 7, 8, 9, 123456789);
        String expectedKey = scheduledDedupeKey(
                NotificationType.LOAN_DUE_SOON, txn.getId(), dueAt);

        Notification created = notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), dueAt,
                "Micros", "Micros").orElseThrow();

        assertThat(created.getDedupeKey()).isEqualTo(expectedKey);
        assertThat(expectedKey).endsWith(":2026-05-06T07:08:09.123456");
    }

    @Test
    void createScheduledRejectsMissingDueAtAndEventTypes() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();

        assertThatThrownBy(() -> notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), null,
                "Missing due date", "Missing due date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("due date");
        assertThatThrownBy(() -> notificationService.createScheduled(borrower,
                NotificationType.LOAN_APPROVED, txn.getId(), SCHEDULED_DUE_AT,
                "Event type", "Event type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheduled notification type");
    }

    @Test
    void scheduledOptOutCreatesNoRowAndLaterOptInCreatesOne() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();
        preferenceService.setPreference(borrower, NotificationType.LOAN_DUE_SOON, false);

        assertThat(notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), SCHEDULED_DUE_AT,
                "Due soon", "Due soon")).isEmpty();
        assertThat(scheduledRowCount(borrower.getId(),
                scheduledDedupeKey(NotificationType.LOAN_DUE_SOON, txn.getId(),
                        SCHEDULED_DUE_AT))).isZero();

        preferenceService.setPreference(borrower, NotificationType.LOAN_DUE_SOON, true);
        Notification first = notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), SCHEDULED_DUE_AT,
                "Due soon", "Due soon").orElseThrow();
        Notification replay = notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), SCHEDULED_DUE_AT,
                "Due soon", "Due soon").orElseThrow();

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(scheduledRowCount(borrower.getId(),
                scheduledDedupeKey(NotificationType.LOAN_DUE_SOON, txn.getId(),
                        SCHEDULED_DUE_AT))).isEqualTo(1);
    }

    @Test
    void scheduledReplayAfterMarkReadPreservesFirstReadTimestamp() {
        Transaction txn = seededTransaction();
        User borrower = txn.getBorrower();

        Notification created = notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), SCHEDULED_DUE_AT,
                "Due soon", "Due soon").orElseThrow();
        Notification marked = notificationRepository.findById(
                notificationService.markAsRead(created.getId(), borrower).id()).orElseThrow();
        LocalDateTime firstReadAt = marked.getReadAt();

        Notification replayed = notificationService.createScheduled(borrower,
                NotificationType.LOAN_DUE_SOON, txn.getId(), SCHEDULED_DUE_AT,
                "Due soon", "Due soon").orElseThrow();

        assertThat(replayed.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(replayed.getReadAt()).isEqualTo(firstReadAt);
        assertThat(scheduledRowCount(borrower.getId(),
                scheduledDedupeKey(NotificationType.LOAN_DUE_SOON, txn.getId(),
                        SCHEDULED_DUE_AT))).isEqualTo(1);
    }
}
