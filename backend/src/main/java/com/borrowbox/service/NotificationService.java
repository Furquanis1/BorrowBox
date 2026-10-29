package com.borrowbox.service;

import com.borrowbox.dto.NotificationResponse;
import com.borrowbox.entity.Notification;
import com.borrowbox.entity.NotificationStatus;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.User;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.NotificationRepository;
import com.borrowbox.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V2.6 notification service.
 *
 * Creates, lists, and marks as read per-user notifications about transaction
 * lifecycle events. All queries are scoped to the authenticated recipient so a
 * user can never see or modify another user's notifications.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a notification for the given recipient. Runs inside the caller's
     * transaction (typically a transaction lifecycle transition), so the
     * notification commits atomically with the state change that triggered it.
     *
     * @param recipient   the user who should receive this notification
     * @param type        the notification type
     * @param title       short user-facing title
     * @param message     full user-facing message
     * @param transactionId the related transaction, or null if none
     * @return the created notification
     */
    @Transactional
    public Notification create(User recipient, NotificationType type, String title,
                               String message, Long transactionId) {
        requireUser(recipient);
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setRecipientEmail(recipient.getEmail());
        n.setTransactionId(transactionId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setStatus(NotificationStatus.UNREAD);
        return notificationRepository.save(n);
    }

    /**
     * Returns all notifications for the given user, newest first.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(User user) {
        requireUser(user);
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDescIdDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns the count of unread notifications for the given user.
     */
    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        requireUser(user);
        return notificationRepository.countByRecipientIdAndStatus(user.getId(),
                NotificationStatus.UNREAD);
    }

    /**
     * Marks the given notification as read. The recipient must own the
     * notification; otherwise a {@link ResourceNotFoundException} is thrown
     * (same message as a missing notification) so another user's notification
     * existence is never disclosed.
     */
    @Transactional
    public NotificationResponse markAsRead(Long id, User user) {
        requireUser(user);
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found with id: " + id));
        if (!n.getRecipient().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Notification not found with id: " + id);
        }
        n.setStatus(NotificationStatus.READ);
        n.setReadAt(LocalDateTime.now());
        return toResponse(notificationRepository.save(n));
    }

    private void requireUser(User user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication is required");
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getTransactionId(),
                n.getStatus(),
                n.getCreatedAt(),
                n.getReadAt()
        );
    }
}
