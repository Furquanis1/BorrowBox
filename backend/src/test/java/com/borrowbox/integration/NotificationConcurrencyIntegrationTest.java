package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.User;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-MySQL concurrency tests for the atomic notification upsert. This class
 * deliberately does not use a test transaction so worker transactions are
 * independent and can race against each other.
 */
@SpringBootTest
@ActiveProfiles("test")
public class NotificationConcurrencyIntegrationTest {

    private static final LocalDateTime SCHEDULED_DUE_AT =
            LocalDateTime.of(2026, 3, 4, 5, 6, 7, 123456789);
    private static final DateTimeFormatter SCHEDULED_DUE_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    @Autowired private SeedDataInitializer seedDataInitializer;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionEventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private String scheduledDedupeKey(NotificationType type, Long transactionId,
                                      LocalDateTime dueAt) {
        return "scheduled:" + type.name() + ":" + transactionId + ":"
                + dueAt.truncatedTo(ChronoUnit.MICROS).format(SCHEDULED_DUE_AT_FORMATTER);
    }

    private Transaction seededTransaction() {
        seedDataInitializer.seed();
        return tx().execute(status -> {
            Transaction txn = transactionRepository.findAll().stream()
                    .filter(candidate -> candidate.getBorrower() != null
                            && candidate.getLender() != null)
                    .findFirst()
                    .orElseThrow();
            txn.getBorrower().getEmail();
            txn.getLender().getEmail();
            return txn;
        });
    }

    private <T> List<T> runConcurrently(int count, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            CyclicBarrier barrier = new CyclicBarrier(count);
            Callable<T> synchronizedTask = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return task.call();
            };
            List<Future<T>> futures = pool.invokeAll(Collections.nCopies(count, synchronizedTask));
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentCreatorsForSameEventConvergeOnOneRow() throws Exception {
        Transaction txn = seededTransaction();
        Long transactionId = txn.getId();
        User borrower = txn.getBorrower();
        TransactionEvent event = tx().execute(status -> {
            Transaction managedTxn = transactionRepository.findById(transactionId).orElseThrow();
            TransactionEvent created = new TransactionEvent();
            created.setTransaction(managedTxn);
            created.setEventType(TransactionEventType.REQUEST_APPROVED);
            created.setActor(managedTxn.getLender());
            return eventRepository.saveAndFlush(created);
        });
        Long eventId = event.getId();
        Long recipientId = borrower.getId();

        try {
            List<Long> ids = runConcurrently(2, () -> tx().execute(status -> {
                TransactionEvent freshEvent = eventRepository.findById(eventId).orElseThrow();
                User recipient = userRepository.findById(recipientId).orElseThrow();
                return notificationService.createForEvent(freshEvent, recipient,
                        NotificationType.LOAN_APPROVED, "Concurrent", "Concurrent")
                        .orElseThrow()
                        .getId();
            }));

            assertThat(ids).containsOnly(ids.get(0));
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM notifications
                     WHERE recipient_id = ? AND source_event_id = ? AND type = ?
                    """, Integer.class, recipientId, eventId, NotificationType.LOAN_APPROVED.name());
            assertThat(count).isEqualTo(1);
        } finally {
            tx().executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM notifications WHERE source_event_id = ?", eventId);
                eventRepository.deleteById(eventId);
            });
        }
    }

    @Test
    void concurrentCreatorsForSameScheduledReminderConvergeOnOneRow() throws Exception {
        Transaction txn = seededTransaction();
        Long transactionId = txn.getId();
        Long recipientId = txn.getBorrower().getId();
        String dedupeKey = scheduledDedupeKey(
                NotificationType.LOAN_DUE_SOON, transactionId, SCHEDULED_DUE_AT);

        try {
            List<Long> ids = runConcurrently(2, () -> tx().execute(status -> {
                User recipient = userRepository.findById(recipientId).orElseThrow();
                return notificationService.createScheduled(recipient,
                        NotificationType.LOAN_DUE_SOON, transactionId, SCHEDULED_DUE_AT,
                        "Due soon", "Due soon")
                        .orElseThrow()
                        .getId();
            }));

            assertThat(ids).containsOnly(ids.get(0));
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM notifications
                     WHERE recipient_id = ? AND dedupe_key = ?
                    """, Integer.class, recipientId, dedupeKey);
            assertThat(count).isEqualTo(1);
        } finally {
            tx().executeWithoutResult(status -> jdbcTemplate.update(
                    "DELETE FROM notifications WHERE recipient_id = ? AND dedupe_key = ?",
                    recipientId, dedupeKey));
        }
    }

    @Test
    void concurrentCreatorsForDifferentDueAtCreateDistinctRows() throws Exception {
        Transaction txn = seededTransaction();
        Long transactionId = txn.getId();
        Long recipientId = txn.getBorrower().getId();
        LocalDateTime firstDueAt = SCHEDULED_DUE_AT;
        LocalDateTime secondDueAt = SCHEDULED_DUE_AT.plusDays(3);
        String firstKey = scheduledDedupeKey(
                NotificationType.LOAN_DUE_SOON, transactionId, firstDueAt);
        String secondKey = scheduledDedupeKey(
                NotificationType.LOAN_DUE_SOON, transactionId, secondDueAt);

        AtomicInteger nextDueAt = new AtomicInteger();
        try {
            List<Long> ids = runConcurrently(2, () -> tx().execute(status -> {
                User recipient = userRepository.findById(recipientId).orElseThrow();
                LocalDateTime dueAt = nextDueAt.getAndIncrement() == 0
                        ? firstDueAt : secondDueAt;
                return notificationService.createScheduled(recipient,
                        NotificationType.LOAN_DUE_SOON, transactionId, dueAt,
                        "Distinct due date", "Distinct due date")
                        .orElseThrow()
                        .getId();
            }));

            assertThat(ids).hasSize(2);
            assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM notifications
                     WHERE recipient_id = ? AND dedupe_key = ?
                    """, Integer.class, recipientId, firstKey)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM notifications
                     WHERE recipient_id = ? AND dedupe_key = ?
                    """, Integer.class, recipientId, secondKey)).isEqualTo(1);
        } finally {
            tx().executeWithoutResult(status -> jdbcTemplate.update(
                    "DELETE FROM notifications WHERE recipient_id = ? AND dedupe_key IN (?, ?)",
                    recipientId, firstKey, secondKey));
        }
    }
}
