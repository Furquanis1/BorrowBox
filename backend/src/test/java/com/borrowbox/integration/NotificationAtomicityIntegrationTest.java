package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.TransactionCreateRequest;
import com.borrowbox.dto.TransactionDecisionRequest;
import com.borrowbox.dto.TransactionResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.NotificationService;
import com.borrowbox.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class NotificationAtomicityIntegrationTest {

    @Autowired private SeedDataInitializer seedDataInitializer;
    @Autowired private TransactionService transactionService;
    @MockitoSpyBean private NotificationService notificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private CommunityListingRepository communityListingRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionEventRepository transactionEventRepository;
    @Autowired private PlatformTransactionManager platformTransactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long createdTransactionId;
    private Long reservedUnitId;
    private Long persistedEventId;

    @AfterEach
    void cleanUp() {
        if (createdTransactionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM webhook_deliveries WHERE transaction_id = ?", createdTransactionId);
            jdbcTemplate.update(
                    "DELETE FROM notifications WHERE transaction_id = ?", createdTransactionId);
            jdbcTemplate.update("""
                    DELETE FROM transaction_event_deliveries
                     WHERE event_id IN (
                         SELECT id FROM transaction_events WHERE transaction_id = ?
                     )
                    """, createdTransactionId);
            jdbcTemplate.update(
                    "DELETE FROM transaction_events WHERE transaction_id = ?", createdTransactionId);
            jdbcTemplate.update(
                    "DELETE FROM transaction_messages WHERE transaction_id = ?", createdTransactionId);
            jdbcTemplate.update(
                    "DELETE FROM transactions WHERE id = ?", createdTransactionId);
            if (reservedUnitId != null) {
                jdbcTemplate.update(
                        "UPDATE asset_units SET status = 'AVAILABLE' WHERE id = ?", reservedUnitId);
            }
        }
        if (persistedEventId != null) {
            jdbcTemplate.update(
                    "DELETE FROM notifications WHERE source_event_id = ?", persistedEventId);
            jdbcTemplate.update(
                    "DELETE FROM transaction_event_deliveries WHERE event_id = ?", persistedEventId);
            jdbcTemplate.update(
                    "DELETE FROM transaction_events WHERE id = ?", persistedEventId);
        }
    }

    @Test
    void approvalRollsBackWhenNotificationCreationFails() {
        seedDataInitializer.seed();
        User lender = seedUser("ahmed@example.com");
        User borrower = seedUser("salah@example.com");
        Asset football = seedAssetOf(lender, "Football");
        CommunityListing listing = cseFootballListing(football);

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(listing.getId(),
                        "Atomicity probe " + UUID.randomUUID(), 2, null), borrower);
        createdTransactionId = created.id();

        reservedUnitId = jdbcTemplate.queryForObject(
                "SELECT reserved_unit_id FROM transactions WHERE id = ?",
                Long.class, created.id());
        String reservedUnitStatus = unitStatus(reservedUnitId);
        int eventsBefore = eventCount(created.id());
        int deliveriesBefore = deliveryCount(created.id());
        int notificationsBefore = notificationCount(created.id());

        NotificationService notificationSpy = AopTestUtils.getTargetObject(notificationService);
        doThrow(new IllegalStateException("forced notification failure"))
                .when(notificationSpy)
                .createForEvent(any(TransactionEvent.class), any(User.class),
                        any(NotificationType.class), anyString(), anyString());

        assertThatThrownBy(() -> transactionService.approve(created.id(),
                new TransactionDecisionRequest("atomicity probe"), lender))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced notification failure");

        String stateAfterFailure = jdbcTemplate.queryForObject(
                "SELECT state FROM transactions WHERE id = ?", String.class, created.id());
        Long unitAfterFailure = jdbcTemplate.queryForObject(
                "SELECT reserved_unit_id FROM transactions WHERE id = ?",
                Long.class, created.id());
        assertThat(stateAfterFailure).isEqualTo(TransactionStatus.PENDING.name());
        assertThat(unitAfterFailure).isEqualTo(reservedUnitId);
        assertThat(eventCount(created.id())).isEqualTo(eventsBefore);
        assertThat(deliveryCount(created.id())).isEqualTo(deliveriesBefore);
        assertThat(notificationCount(created.id())).isEqualTo(notificationsBefore);
        assertThat(unitStatus(reservedUnitId)).isEqualTo(reservedUnitStatus);
        assertThat(unitStatus(reservedUnitId)).isEqualTo("RESERVED");
    }

    @Test
    void createForEventRequiresAnActiveCallerTransaction() {
        seedDataInitializer.seed();
        Long seededTransactionId = jdbcTemplate.queryForObject(
                "SELECT id FROM transactions ORDER BY id LIMIT 1", Long.class);
        Long borrowerId = jdbcTemplate.queryForObject(
                "SELECT borrower_id FROM transactions WHERE id = ?",
                Long.class, seededTransactionId);

        TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
        persistedEventId = transactionTemplate.execute(status -> {
            var managedTransaction = transactionRepository
                    .findById(seededTransactionId)
                    .orElseThrow();
            TransactionEvent event = new TransactionEvent();
            event.setTransaction(managedTransaction);
            event.setEventType(TransactionEventType.REQUEST_APPROVED);
            event.setActor(managedTransaction.getLender());
            return transactionEventRepository.saveAndFlush(event).getId();
        });

        User recipient = userRepository.findById(borrowerId).orElseThrow();
        int notificationsBefore = notificationCountForEvent(recipient.getId(), persistedEventId);

        assertThatThrownBy(() -> notificationService.createForEvent(
                transactionEventRepository.findById(persistedEventId).orElseThrow(),
                recipient,
                NotificationType.LOAN_APPROVED,
                "No caller transaction",
                "This call must be rejected"))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining("mandatory");

        assertThat(notificationCountForEvent(recipient.getId(), persistedEventId))
                .isEqualTo(notificationsBefore)
                .isZero();
    }

    private User seedUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("missing seed user " + email));
    }

    private Asset seedAssetOf(User owner, String title) {
        return assetRepository.findByOwnerId(owner.getId()).stream()
                .filter(asset -> asset.getTitle().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed asset " + title));
    }

    private CommunityListing cseFootballListing(Asset football) {
        return communityListingRepository
                .findByAssetIdAndCommunityId(football.getId(), cseId())
                .orElseThrow(() -> new AssertionError("Missing Football listing in CSE"));
    }

    private Long cseId() {
        return communityRepository.findAll().stream()
                .filter(community -> community.getName().equals("CSE Department"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed community CSE Department"))
                .getId();
    }

    private String unitStatus(Long unitId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM asset_units WHERE id = ?", String.class, unitId);
    }

    private int eventCount(Long transactionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_events WHERE transaction_id = ?",
                Integer.class, transactionId);
    }

    private int deliveryCount(Long transactionId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM transaction_event_deliveries d
                  JOIN transaction_events e ON e.id = d.event_id
                 WHERE e.transaction_id = ?
                """, Integer.class, transactionId);
    }

    private int notificationCount(Long transactionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE transaction_id = ?",
                Integer.class, transactionId);
    }

    private int notificationCountForEvent(Long recipientId, Long eventId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM notifications
                 WHERE recipient_id = ? AND source_event_id = ?
                """, Integer.class, recipientId, eventId);
    }
}
