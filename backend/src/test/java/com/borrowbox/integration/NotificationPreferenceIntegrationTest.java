package com.borrowbox.integration;

import com.borrowbox.dto.NotificationPreferenceResponse;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
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
    @Autowired private PasswordEncoder passwordEncoder;

    private User createUser(String email) {
        User u = new User("Test User", email);
        u.setPasswordHash(passwordEncoder.encode("password"));
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
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

        // Opt out of LOAN_APPROVED
        preferenceService.setPreference(user,
                com.borrowbox.entity.NotificationType.LOAN_APPROVED, false);

        // Creating a notification for an opted-out type should return null (skipped)
        com.borrowbox.entity.Notification created = notificationService.create(user,
                com.borrowbox.entity.NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.", null);
        assertThat(created).isNull();

        // But a different type should still be created
        com.borrowbox.entity.Notification created2 = notificationService.create(user,
                com.borrowbox.entity.NotificationType.LOAN_REJECTED,
                "Loan rejected", "Your loan request was rejected.", null);
        assertThat(created2).isNotNull();
        assertThat(created2.getId()).isNotNull();
    }

    @Test
    void defaultEnabledCreatesNotification() {
        User user = createUser("default-" + UUID.randomUUID() + "@example.com");

        // No preference set -> default enabled
        com.borrowbox.entity.Notification created = notificationService.create(user,
                com.borrowbox.entity.NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.", null);
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
    }
}
