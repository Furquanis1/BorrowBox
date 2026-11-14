package com.borrowbox.integration;

import com.borrowbox.entity.Asset;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.Notification;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.NotificationRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.NotificationSchedulerService;
import com.borrowbox.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@TestConfiguration(proxyBeanMethods = false)
class NoAutomaticSchedulingConfig {

    @Bean(name = "taskScheduler")
    @Primary
    ThreadPoolTaskScheduler noOpTaskScheduler() {
        return new ThreadPoolTaskScheduler() {
            @Override
            public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
                return null;
            }

            @Override
            public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
                return null;
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime,
                                                          Duration period) {
                return null;
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
                return null;
            }

            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime,
                                                             Duration delay) {
                return null;
            }

            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
                return null;
            }
        };
    }
}

/**
 * Proves that the scheduled reminder sweep runs in a writable transaction and
 * that its notifications are committed and visible after the scheduler method
 * returns. The test deliberately runs without a test-managed transaction so the
 * scheduler must open its own transaction, matching production execution.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(NoAutomaticSchedulingConfig.class)
public class NotificationSchedulerTransactionTest {

    @Autowired private NotificationSchedulerService notificationSchedulerService;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityService communityService;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private CommunityListingRepository communityListingRepository;

    @MockitoSpyBean(name = "transactionRepository")
    private TransactionRepository transactionRepository;

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoSpyBean(name = "notificationService")
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    private final List<SchedulerObservation> schedulerObservations = new CopyOnWriteArrayList<>();
    private final List<CreateObservation> createObservations = new CopyOnWriteArrayList<>();
    private final Map<Long, Object> schedulerHolders = new ConcurrentHashMap<>();
    private final Set<Object> registeredSchedulerHolders = ConcurrentHashMap.newKeySet();
    private final AtomicInteger committedSchedulerTransactions = new AtomicInteger();
    private final List<Integer> completionStatuses = new CopyOnWriteArrayList<>();
    private Long transactionId;
    private Long failCreateForRecipientId;
    private Transaction fixtureTransaction;

    @BeforeEach
    void observeSchedulerTransaction() {
        schedulerObservations.clear();
        createObservations.clear();
        schedulerHolders.clear();
        registeredSchedulerHolders.clear();
        committedSchedulerTransactions.set(0);
        completionStatuses.clear();
        transactionId = null;
        failCreateForRecipientId = null;
        fixtureTransaction = null;

        doAnswer(invocation -> dueBetween(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2)))
                .when(transactionRepository)
                .findByStateAndDueAtBetween(any(), any(), any());
        doAnswer(invocation -> dueBefore(
                invocation.getArgument(0),
                invocation.getArgument(1)))
                .when(transactionRepository)
                .findByStateAndDueAtBefore(any(), any());

        doAnswer(invocation -> {
            Long observedTransactionId = invocation.getArgument(2);
            if (Objects.equals(observedTransactionId, transactionId)) {
                boolean active = TransactionSynchronizationManager.isActualTransactionActive();
                boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                boolean synchronizationActive =
                        TransactionSynchronizationManager.isSynchronizationActive();
                Object holder = TransactionSynchronizationManager.getResource(entityManagerFactory);
                schedulerObservations.add(new SchedulerObservation(
                        observedTransactionId, active, readOnly, synchronizationActive, holder));
                if (holder != null) {
                    schedulerHolders.put(observedTransactionId, holder);
                } else {
                    schedulerHolders.remove(observedTransactionId);
                }
                if (synchronizationActive && holder != null
                        && registeredSchedulerHolders.add(holder)) {
                    TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    committedSchedulerTransactions.incrementAndGet();
                                }

                                @Override
                                public void afterCompletion(int status) {
                                    completionStatuses.add(status);
                                }
                            });
                }
            }
            return invocation.callRealMethod();
        }).when(notificationService).notificationExists(any(), any(), any());

        doAnswer(invocation -> {
            User recipient = invocation.getArgument(0);
            NotificationType type = invocation.getArgument(1);
            Long observedTransactionId = invocation.getArgument(4);
            Long recipientId = recipient == null ? null : recipient.getId();
            if (Objects.equals(observedTransactionId, transactionId)) {
                Object holder = TransactionSynchronizationManager.getResource(entityManagerFactory);
                createObservations.add(new CreateObservation(
                        observedTransactionId,
                        recipientId,
                        TransactionSynchronizationManager.isActualTransactionActive(),
                        TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                        holder,
                        holder != null && holder == schedulerHolders.get(observedTransactionId)));
            }
            if (Objects.equals(observedTransactionId, transactionId)
                    && Objects.equals(recipientId, failCreateForRecipientId)
                    && type == NotificationType.LOAN_OVERDUE) {
                throw new IllegalStateException("controlled lender notification failure");
            }
            return invocation.callRealMethod();
        }).when(notificationService).create(any(), any(), any(), any(), any());
    }

    @Test
    void schedulerRunsInWritableTransactionAndPersistsNotifications() {
        CreatedWorld world = createWorld();
        transactionId = world.transactionId();
        try {
            notificationSchedulerService.checkDueSoonAndOverdue();

            assertThat(schedulerObservations)
                    .as("scheduler must execute notification checks with an actual transaction")
                    .isNotEmpty()
                    .allSatisfy(observation -> {
                        assertThat(observation.active()).isTrue();
                        assertThat(observation.readOnly()).isFalse();
                        assertThat(observation.synchronizationActive()).isTrue();
                        assertThat(observation.holder()).isNotNull();
                    });
            assertThat(createObservations)
                    .as("NotificationService.create must run inside the scheduler transaction")
                    .isNotEmpty()
                    .allSatisfy(create -> {
                        assertThat(create.active()).isTrue();
                        assertThat(create.readOnly()).isFalse();
                        assertThat(create.holder()).isNotNull();
                        assertThat(create.joinsSchedulerTransaction()).isTrue();
                    });
            assertThat(committedSchedulerTransactions.get())
                    .as("scheduler transaction must commit")
                    .isPositive();
            assertThat(completionStatuses)
                    .isNotEmpty()
                    .containsOnly(TransactionSynchronization.STATUS_COMMITTED);

            assertThat(notificationsFor(world.borrowerId(), NotificationType.LOAN_DUE_SOON,
                    world.transactionId())).hasSize(1);

            notificationSchedulerService.checkDueSoonAndOverdue();
            assertThat(notificationsFor(world.borrowerId(), NotificationType.LOAN_DUE_SOON,
                    world.transactionId())).hasSize(1);

            markTransactionOverdue(world.transactionId());
            notificationSchedulerService.checkDueSoonAndOverdue();
            notificationSchedulerService.checkDueSoonAndOverdue();

            assertThat(notificationsFor(world.borrowerId(), NotificationType.LOAN_OVERDUE,
                    world.transactionId())).hasSize(1);
            assertThat(notificationsFor(world.lenderId(), NotificationType.LOAN_OVERDUE,
                    world.transactionId())).hasSize(1);
            assertThat(notificationsFor(world.borrowerId(), NotificationType.LOAN_DUE_SOON,
                    world.transactionId())).hasSize(1);

            assertThat(createObservations)
                    .hasSize(3)
                    .allSatisfy(create -> {
                        assertThat(create.active()).isTrue();
                        assertThat(create.readOnly()).isFalse();
                        assertThat(create.holder()).isNotNull();
                        assertThat(create.joinsSchedulerTransaction()).isTrue();
                    });
            assertThat(completionStatuses)
                    .isNotEmpty()
                    .containsOnly(TransactionSynchronization.STATUS_COMMITTED);
        } finally {
            deleteWorld(world);
        }
    }

    @Test
    void schedulerRollsBackWholeSweepWhenLenderNotificationCreationFails() {
        CreatedWorld world = createWorld();
        transactionId = world.transactionId();
        failCreateForRecipientId = world.lenderId();
        try {
            markTransactionOverdue(world.transactionId());

            assertThatThrownBy(notificationSchedulerService::checkDueSoonAndOverdue)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("controlled lender notification failure");

            assertThat(createObservations)
                    .as("borrower then lender creation must both be attempted")
                    .extracting(CreateObservation::recipientId)
                    .containsExactly(world.borrowerId(), world.lenderId());
            assertThat(createObservations)
                    .allSatisfy(create -> {
                        assertThat(create.active()).isTrue();
                        assertThat(create.readOnly()).isFalse();
                        assertThat(create.holder()).isNotNull();
                        assertThat(create.joinsSchedulerTransaction()).isTrue();
                    });
            assertThat(completionStatuses)
                    .as("scheduler transaction must roll back the earlier borrower notification")
                    .containsOnly(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertThat(notificationsFor(world.borrowerId(), NotificationType.LOAN_OVERDUE,
                    world.transactionId())).isEmpty();
            assertThat(notificationsFor(world.lenderId(), NotificationType.LOAN_OVERDUE,
                    world.transactionId())).isEmpty();
        } finally {
            failCreateForRecipientId = null;
            deleteWorld(world);
        }
    }

    private CreatedWorld createWorld() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        CreatedWorld world = template.execute(status -> {
            User manager = V242IntegrationFixture.newUser(userRepository, "SchedulerTxMgr");
            User lender = V242IntegrationFixture.newUser(userRepository, "SchedulerTxLender");
            User borrower = V242IntegrationFixture.newUser(userRepository, "SchedulerTxBorrower");
            Community community = V242IntegrationFixture.newCommunity(
                    communityService, communityRepository, manager);
            Asset asset = V242IntegrationFixture.newAsset(
                    assetRepository, lender, "Scheduler Tx Tool");
            CommunityListing listing = V242IntegrationFixture.newListing(
                    communityListingRepository, asset, community);
            Transaction txn = V242IntegrationFixture.newTransaction(
                    transactionRepository, community, listing, asset, borrower, lender,
                    TransactionStatus.ACTIVE, LocalDateTime.now().plusHours(1), null);
            fixtureTransaction = txn;
            return new CreatedWorld(community.getId(), listing.getId(), asset.getId(),
                    manager.getId(), borrower.getId(), lender.getId(), txn.getId());
        });
        return Objects.requireNonNull(world);
    }

    private void markTransactionOverdue(Long transactionId) {
        LocalDateTime overdueDueAt = LocalDateTime.now().minusHours(1);
        jdbcTemplate.update("UPDATE transactions SET due_at = ? WHERE id = ?",
                Timestamp.valueOf(overdueDueAt), transactionId);
        if (fixtureTransaction != null
                && Objects.equals(transactionId, fixtureTransaction.getId())) {
            fixtureTransaction.setDueAt(overdueDueAt);
        }
    }

    private List<Transaction> dueBetween(TransactionStatus state, LocalDateTime start,
                                          LocalDateTime end) {
        Transaction fixture = fixtureTransaction;
        if (fixture == null || fixture.getState() != state || fixture.getDueAt() == null
                || fixture.getDueAt().isBefore(start)
                || fixture.getDueAt().isAfter(end)) {
            return List.of();
        }
        return List.of(queryFixtureTransaction());
    }

    private List<Transaction> dueBefore(TransactionStatus state, LocalDateTime cutoff) {
        Transaction fixture = fixtureTransaction;
        if (fixture == null || fixture.getState() != state || fixture.getDueAt() == null
                || !fixture.getDueAt().isBefore(cutoff)) {
            return List.of();
        }
        return List.of(queryFixtureTransaction());
    }

    private Transaction queryFixtureTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            Transaction managed = entityManager.find(Transaction.class, fixtureTransaction.getId());
            return managed != null ? managed : fixtureTransaction;
        }
        return fixtureTransaction;
    }

    private List<Notification> notificationsFor(Long recipientId, NotificationType type,
                                                 Long transactionId) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDescIdDesc(recipientId)
                .stream()
                .filter(n -> n.getType() == type
                        && transactionId.equals(n.getTransactionId()))
                .toList();
    }

    private void deleteWorld(CreatedWorld world) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "DELETE FROM notifications WHERE transaction_id = ? OR recipient_id IN (?, ?, ?)",
                    world.transactionId(), world.managerId(), world.borrowerId(), world.lenderId());
            jdbcTemplate.update(
                    "DELETE FROM notification_preferences WHERE user_id IN (?, ?, ?)",
                    world.managerId(), world.borrowerId(), world.lenderId());
            jdbcTemplate.update("DELETE FROM transactions WHERE id = ?", world.transactionId());
            jdbcTemplate.update("DELETE FROM community_listings WHERE id = ?", world.listingId());
            jdbcTemplate.update("DELETE FROM assets WHERE id = ?", world.assetId());
            jdbcTemplate.update(
                    "DELETE FROM memberships WHERE community_id = ? OR user_id IN (?, ?, ?)",
                    world.communityId(), world.managerId(), world.borrowerId(), world.lenderId());
            jdbcTemplate.update("DELETE FROM communities WHERE id = ?", world.communityId());
            jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?)",
                    world.managerId(), world.borrowerId(), world.lenderId());
        });
    }

    private record SchedulerObservation(Long transactionId, boolean active, boolean readOnly,
                                        boolean synchronizationActive, Object holder) {
    }

    private record CreateObservation(Long transactionId, Long recipientId, boolean active,
                                     boolean readOnly, Object holder,
                                     boolean joinsSchedulerTransaction) {
    }

    private record CreatedWorld(Long communityId, Long listingId, Long assetId,
                                Long managerId, Long borrowerId, Long lenderId,
                                Long transactionId) {
    }
}
