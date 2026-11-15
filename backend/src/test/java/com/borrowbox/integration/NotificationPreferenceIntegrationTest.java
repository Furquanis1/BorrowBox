package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.NotificationPreferenceResponse;
import com.borrowbox.entity.Notification;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.NotificationPreferenceService;
import com.borrowbox.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the V2.6 notification preference service: creating/updating
 * preferences per user+type, listing all preferences for a user, and
 * preference-aware notification creation (skipping when a user has opted out).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class NotificationPreferenceIntegrationTest {

    @Autowired private NotificationPreferenceService preferenceService;
    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionEventRepository transactionEventRepository;
    @Autowired private SeedDataInitializer seedDataInitializer;
    @Autowired private PasswordEncoder passwordEncoder;

    private User createUser(String email) {
        User u = new User("Test User", email);
        u.setPasswordHash(passwordEncoder.encode("password"));
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

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
        return transactionEventRepository.saveAndFlush(event);
    }

    @Test
    void setAndGetPreferences() {
        User user = createUser("pref-" + UUID.randomUUID() + "@example.com");

        NotificationPreferenceResponse created = preferenceService.setPreference(user,
                com.borrowbox.entity.NotificationType.LOAN_APPROVED, false);
        assertThat(created.type()).isEqualTo(com.borrowbox.entity.NotificationType.LOAN_APPROVED);
        assertThat(created.enabled()).isFalse();

        NotificationPreferenceResponse created2 = preferenceService.setPreference(user,
                com.borrowbox.entity.NotificationType.LOAN_REJECTED, true);
        assertThat(created2.type()).isEqualTo(com.borrowbox.entity.NotificationType.LOAN_REJECTED);
        assertThat(created2.enabled()).isTrue();

        List<NotificationPreferenceResponse> all = preferenceService.getPreferences(user);
        assertThat(all).hasSize(2);
        assertThat(all.stream().map(NotificationPreferenceResponse::type))
                .containsExactlyInAnyOrder(
                        com.borrowbox.entity.NotificationType.LOAN_APPROVED,
                        com.borrowbox.entity.NotificationType.LOAN_REJECTED);
    }

    @Test
    void optedOutUserDoesNotReceiveNotification() {
        User user = createUser("optout-" + UUID.randomUUID() + "@example.com");
        Transaction txn = seededTransaction();

        // Opt out of LOAN_APPROVED
        preferenceService.setPreference(user, NotificationType.LOAN_APPROVED, false);

        // Creating a notification for an opted-out type is suppressed.
        Optional<Notification> created = notificationService.createForEvent(
                persistEvent(txn, TransactionEventType.REQUEST_APPROVED), user,
                NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.");
        assertThat(created).isEmpty();

        // But a different type should still be created
        Notification created2 = notificationService.createForEvent(
                persistEvent(txn, TransactionEventType.REQUEST_REJECTED), user,
                NotificationType.LOAN_REJECTED,
                "Loan rejected", "Your loan request was rejected.").orElseThrow();
        assertThat(created2.getId()).isNotNull();
    }

    @Test
    void defaultEnabledCreatesNotification() {
        User user = createUser("default-" + UUID.randomUUID() + "@example.com");
        Transaction txn = seededTransaction();

        // No preference set -> default enabled
        Notification created = notificationService.createForEvent(
                persistEvent(txn, TransactionEventType.REQUEST_APPROVED), user,
                NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.").orElseThrow();
        assertThat(created.getId()).isNotNull();
    }
}
