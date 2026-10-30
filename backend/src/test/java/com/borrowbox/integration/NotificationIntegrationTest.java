package com.borrowbox.integration;

import com.borrowbox.dto.NotificationResponse;
import com.borrowbox.entity.Notification;
import com.borrowbox.entity.NotificationStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.NotificationRepository;
import com.borrowbox.repository.UserRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the V2.6 notification service: creating notifications from
 * transaction lifecycle transitions, listing a user's notifications, unread
 * count, marking as read, and cross-user access rejection.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class NotificationIntegrationTest {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User createUser(String email) {
        User u = new User("Test User", email);
        u.setPasswordHash(passwordEncoder.encode("password"));
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    @Test
    void createdNotificationAppearsInRecipientList() {
        User borrower = createUser("borrower-" + UUID.randomUUID() + "@example.com");
        User lender = createUser("lender-" + UUID.randomUUID() + "@example.com");

        notificationService.create(borrower, com.borrowbox.entity.NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.", 1L);

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

        notificationService.create(user, com.borrowbox.entity.NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.", null);
        notificationService.create(user, com.borrowbox.entity.NotificationType.LOAN_REJECTED,
                "Loan rejected", "Your loan request was rejected.", null);

        assertThat(notificationService.unreadCount(user)).isEqualTo(2);

        List<NotificationResponse> notifications = notificationService.listForUser(user);
        notificationService.markAsRead(notifications.get(0).id(), user);

        assertThat(notificationService.unreadCount(user)).isEqualTo(1);
    }

    @Test
    void markAsReadUpdatesStatus() {
        User user = createUser("read-" + UUID.randomUUID() + "@example.com");

        Notification createdEntity = notificationService.create(user,
                com.borrowbox.entity.NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.", null);
        NotificationResponse created = notificationService.toResponse(createdEntity);

        NotificationResponse marked = notificationService.markAsRead(created.id(), user);

        assertThat(marked.status()).isEqualTo(NotificationStatus.READ);
        assertThat(marked.readAt()).isNotNull();

        List<NotificationResponse> all = notificationService.listForUser(user);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).status()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    void markAsReadRejectsOtherUsersNotification() {
        User owner = createUser("owner-" + UUID.randomUUID() + "@example.com");
        User other = createUser("other-" + UUID.randomUUID() + "@example.com");

        Notification createdEntity = notificationService.create(owner,
                com.borrowbox.entity.NotificationType.LOAN_APPROVED,
                "Loan approved", "Your loan request was approved.", null);
        NotificationResponse created = notificationService.toResponse(createdEntity);

        assertThatThrownBy(() -> notificationService.markAsRead(created.id(), other))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found with id:");
    }
}
