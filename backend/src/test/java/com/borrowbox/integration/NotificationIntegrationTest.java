package com.borrowbox.integration;

import com.borrowbox.dto.NotificationResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.Notification;
import com.borrowbox.entity.NotificationStatus;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.NotificationRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.NotificationSchedulerService;
import com.borrowbox.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the V2.6 notification service: listing a user's notifications,
 * unread count, marking as read, and cross-user access rejection.
 */
@SpringBootTest(properties = "borrowbox.notification.scheduler.enabled=true")
@ActiveProfiles("test")
@Transactional
@Import(NoAutomaticSchedulingConfig.class)
public class NotificationIntegrationTest {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationSchedulerService notificationSchedulerService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityService communityService;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private CommunityListingRepository communityListingRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionEventRepository transactionEventRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User createUser(String email) {
        User u = new User("Test User", email);
        u.setPasswordHash(passwordEncoder.encode("password"));
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(u);
    }

    private Notification insertNotification(User user, NotificationType type,
                                            String title, String message) {
        Notification notification = new Notification();
        notification.setRecipient(user);
        notification.setRecipientEmail(user.getEmail());
        notification.setDedupeKey("test:" + type.name() + ":" + UUID.randomUUID());
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setStatus(NotificationStatus.UNREAD);
        return notificationRepository.saveAndFlush(notification);
    }

    private TransactionEvent persistEvent(Transaction txn, TransactionEventType type) {
        TransactionEvent event = new TransactionEvent();
        event.setTransaction(txn);
        event.setEventType(type);
        event.setActor(txn.getLender());
        return transactionEventRepository.saveAndFlush(event);
    }

    private record NotificationWorld(Community community, CommunityListing listing, Asset asset,
                                     User borrower, User lender) {
    }

    private NotificationWorld notificationWorld() {
        User manager = V242IntegrationFixture.newUser(userRepository, "NotifyMgr");
        User lender = V242IntegrationFixture.newUser(userRepository, "NotifyLender");
        User borrower = V242IntegrationFixture.newUser(userRepository, "NotifyBorrower");
        Community community = V242IntegrationFixture.newCommunity(
                communityService, communityRepository, manager);
        Asset asset = V242IntegrationFixture.newAsset(
                assetRepository, lender, "Notification Tool");
        CommunityListing listing = V242IntegrationFixture.newListing(
                communityListingRepository, asset, community);
        return new NotificationWorld(community, listing, asset, borrower, lender);
    }

    @Test
    void createdNotificationAppearsInRecipientList() {
        User borrower = createUser("borrower-" + UUID.randomUUID() + "@example.com");
        User lender = createUser("lender-" + UUID.randomUUID() + "@example.com");

        insertNotification(borrower, NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.");

        List<NotificationResponse> borrowerNotifications =
                notificationService.listForUser(borrower);
        assertThat(borrowerNotifications).hasSize(1);
        assertThat(borrowerNotifications.get(0).title()).isEqualTo("Loan approved");
        assertThat(borrowerNotifications.get(0).status()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(borrowerNotifications.get(0).recipientEmail()).isEqualTo(borrower.getEmail());

        // Lender should not see borrower's notification.
        List<NotificationResponse> lenderNotifications =
                notificationService.listForUser(lender);
        assertThat(lenderNotifications).isEmpty();
        assertThat(notificationService.unreadCount(lender)).isZero();
    }

    @Test
    void unreadCountReflectsUnreadNotifications() {
        User user = createUser("count-" + UUID.randomUUID() + "@example.com");

        assertThat(notificationService.unreadCount(user)).isZero();

        insertNotification(user, NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.");
        insertNotification(user, NotificationType.LOAN_REJECTED,
                "Loan rejected", "Your loan request was rejected.");

        assertThat(notificationService.unreadCount(user)).isEqualTo(2);

        List<NotificationResponse> notifications = notificationService.listForUser(user);
        notificationService.markAsRead(notifications.get(0).id(), user);

        assertThat(notificationService.unreadCount(user)).isEqualTo(1);
    }

    @Test
    void markAsReadUpdatesStatus() {
        User user = createUser("read-" + UUID.randomUUID() + "@example.com");

        Notification created = insertNotification(user, NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.");

        NotificationResponse marked = notificationService.markAsRead(created.getId(), user);

        assertThat(marked.status()).isEqualTo(NotificationStatus.READ);
        assertThat(marked.readAt()).isNotNull();

        List<NotificationResponse> all = notificationService.listForUser(user);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).status()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    void historicalRowWithNullIdempotencyKeysIsReadable() {
        User user = createUser("historical-" + UUID.randomUUID() + "@example.com");

        Notification historical = new Notification();
        historical.setRecipient(user);
        historical.setRecipientEmail(user.getEmail());
        historical.setTransactionId(null);
        historical.setSourceEventId(null);
        historical.setDedupeKey(null);
        historical.setType(NotificationType.LOAN_APPROVED);
        historical.setTitle("Historical notification");
        historical.setMessage("Created before idempotency keys existed.");
        historical.setStatus(NotificationStatus.UNREAD);
        notificationRepository.saveAndFlush(historical);

        List<NotificationResponse> notifications = notificationService.listForUser(user);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).title()).isEqualTo("Historical notification");
        assertThat(notificationService.unreadCount(user)).isEqualTo(1);
    }

    @Test
    void markAsReadRejectsOtherUsersNotification() {
        User owner = createUser("owner-" + UUID.randomUUID() + "@example.com");
        User other = createUser("other-" + UUID.randomUUID() + "@example.com");

        Notification created = insertNotification(owner, NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.");

        assertThatThrownBy(() -> notificationService.markAsRead(created.getId(), other))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found with id:");
    }

    @Test
    void sameRecipientTypeAndTransactionCanRepeat() {
        NotificationWorld w = notificationWorld();
        Transaction txn = V242IntegrationFixture.newTransaction(
                transactionRepository, w.community(), w.listing(), w.asset(),
                w.borrower(), w.lender(), TransactionStatus.ACTIVE,
                LocalDateTime.now().plusDays(2), null);

        Notification first = notificationService.createForEvent(
                persistEvent(txn, TransactionEventType.EXTENSION_REQUESTED),
                w.lender(), NotificationType.EXTENSION_REQUESTED,
                "Extension requested", "First request.").orElseThrow();
        Long firstId = first.getId();
        notificationService.markAsRead(firstId, w.lender());

        Notification reactivated = notificationService.createForEvent(
                persistEvent(txn, TransactionEventType.EXTENSION_REQUESTED),
                w.lender(), NotificationType.EXTENSION_REQUESTED,
                "Extension requested", "Second request.").orElseThrow();

        List<Notification> matches = notificationRepository
                .findByRecipientIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        w.lender().getId(), NotificationStatus.SUPPRESSED)
                .stream()
                .filter(n -> n.getType() == NotificationType.EXTENSION_REQUESTED
                        && txn.getId().equals(n.getTransactionId()))
                .toList();

        assertThat(reactivated.getId()).isEqualTo(firstId);
        assertThat(matches).hasSize(1);
        Notification reactivatedRow = matches.get(0);
        assertThat(reactivatedRow.getId()).isEqualTo(firstId);
        assertThat(reactivatedRow.getMessage()).isEqualTo("Second request.");
        assertThat(reactivatedRow.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(reactivatedRow.getReadAt()).isNull();
    }

    @Test
    void schedulerDeduplicatesDueSoonAndOverdueNotifications() {
        NotificationWorld w = notificationWorld();
        LocalDateTime now = LocalDateTime.now();
        Transaction txn = V242IntegrationFixture.newTransaction(
                transactionRepository, w.community(), w.listing(), w.asset(),
                w.borrower(), w.lender(), TransactionStatus.ACTIVE,
                now.plusHours(1), null);

        notificationSchedulerService.checkDueSoonAndOverdue();
        notificationSchedulerService.checkDueSoonAndOverdue();

        assertThat(notificationRepository
                .findByRecipientIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        w.borrower().getId(), NotificationStatus.SUPPRESSED))
                .filteredOn(n -> n.getType() == NotificationType.LOAN_DUE_SOON
                        && txn.getId().equals(n.getTransactionId()))
                .hasSize(1);

        txn.setDueAt(now.minusHours(1));
        transactionRepository.saveAndFlush(txn);

        notificationSchedulerService.checkDueSoonAndOverdue();
        notificationSchedulerService.checkDueSoonAndOverdue();

        assertThat(notificationRepository
                .findByRecipientIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        w.borrower().getId(), NotificationStatus.SUPPRESSED))
                .filteredOn(n -> n.getType() == NotificationType.LOAN_OVERDUE
                        && txn.getId().equals(n.getTransactionId()))
                .hasSize(1);
        assertThat(notificationRepository
                .findByRecipientIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        w.lender().getId(), NotificationStatus.SUPPRESSED))
                .filteredOn(n -> n.getType() == NotificationType.LOAN_OVERDUE
                        && txn.getId().equals(n.getTransactionId()))
                .hasSize(1);
    }
}
