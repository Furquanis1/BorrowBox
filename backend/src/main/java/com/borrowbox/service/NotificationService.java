package com.borrowbox.service;

import com.borrowbox.dto.NotificationResponse;
import com.borrowbox.entity.Notification;
import com.borrowbox.entity.NotificationStatus;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.User;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.NotificationPreferenceRepository;
import com.borrowbox.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * V2.6 notification service.
 *
 * Creates, lists, and marks as read per-user notifications about transaction
 * lifecycle events. All queries are scoped to the authenticated recipient so a
 * user can never see or modify another user's notifications.
 */
@Service
public class NotificationService {

    private static final String SCHEDULED_DEDUPE_PREFIX = "scheduled:";
    private static final DateTimeFormatter SCHEDULED_DUE_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
    private static final Set<NotificationType> SCHEDULED_TYPES =
            Set.of(NotificationType.LOAN_DUE_SOON, NotificationType.LOAN_OVERDUE);
    private static final Set<NotificationType> EXTENSION_TYPES = Set.of(
            NotificationType.EXTENSION_REQUESTED,
            NotificationType.EXTENSION_APPROVED,
            NotificationType.EXTENSION_REJECTED,
            NotificationType.EXTENSION_COUNTERED,
            NotificationType.EXTENSION_COUNTER_ACCEPTED,
            NotificationType.EXTENSION_COUNTER_REJECTED);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceRepository preferenceRepository) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Creates (or reuses) the notification for one recipient of a transaction
     * event. The event must already be persisted and this method must run inside
     * the caller's transaction so the notification commits atomically with the
     * transaction state change and event row.
     *
     * <p>Extension notification types preserve the V2.6.1 reactivation behavior:
     * a later extension event for the same recipient, type, and transaction
     * resets the existing row to unread instead of inserting a new row. Replaying
     * the same source event returns the existing row unchanged.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Notification> createForEvent(TransactionEvent event,
                                                 User recipient,
                                                 NotificationType type,
                                                 String title,
                                                 String message) {
        requireUser(recipient);
        if (event == null || event.getId() == null) {
            throw new IllegalArgumentException("A persisted transaction event is required");
        }

        Long recipientId = recipient.getId();
        Long transactionId = event.getTransaction() != null
                ? event.getTransaction().getId()
                : null;
        boolean extensionNotification = EXTENSION_TYPES.contains(type);
        boolean optedOut = isOptedOut(recipientId, type);

        if (extensionNotification) {
            if (optedOut) {
                return Optional.empty();
            }
            Optional<Notification> existing = notificationRepository
                    .findFirstByRecipientIdAndTypeAndTransactionIdOrderByIdAsc(
                            recipientId, type, transactionId);
            if (existing.isPresent()) {
                Notification notification = existing.get();
                if (event.getId().equals(notification.getSourceEventId())) {
                    return notification.getStatus() == NotificationStatus.SUPPRESSED
                            ? Optional.empty()
                            : Optional.of(notification);
                }
                notification.setRecipient(recipient);
                notification.setRecipientEmail(recipient.getEmail());
                notification.setTransactionId(transactionId);
                notification.setType(type);
                notification.setTitle(title);
                notification.setMessage(message);
                notification.setStatus(NotificationStatus.UNREAD);
                notification.setReadAt(null);
                notification.setSourceEventId(event.getId());
                notification.setDedupeKey(null);
                notification.setCreatedAt(LocalDateTime.now());
                return Optional.of(notificationRepository.save(notification));
            }
        }

        NotificationStatus initialStatus = optedOut
                ? NotificationStatus.SUPPRESSED
                : NotificationStatus.UNREAD;

        notificationRepository.insertEventNotificationIfAbsent(
                recipientId,
                recipient.getEmail(),
                transactionId,
                event.getId(),
                type.name(),
                title,
                message,
                initialStatus.name(),
                LocalDateTime.now());

        Notification canonical = notificationRepository
                .findEventNotificationForUpdate(recipientId, event.getId(), type)
                .orElseThrow(() -> new IllegalStateException(
                        "Notification was not found after event insert"));
        return canonical.getStatus() == NotificationStatus.SUPPRESSED
                ? Optional.empty()
                : Optional.of(canonical);
    }

    /**
     * Creates (or reuses) one scheduled reminder for a recipient. Opted-out
     * recipients get no row at all, so a later opt-in may create the reminder
     * while the underlying due/overdue condition still holds.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Notification> createScheduled(User recipient,
                                                  NotificationType type,
                                                  Long transactionId,
                                                  LocalDateTime dueAt,
                                                  String title,
                                                  String message) {
        requireUser(recipient);
        if (type == null || !SCHEDULED_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Scheduled notifications require a scheduled notification type");
        }
        if (transactionId == null) {
            throw new IllegalArgumentException("A transaction id is required for scheduled notifications");
        }
        if (dueAt == null) {
            throw new IllegalArgumentException("A due date is required for scheduled notifications");
        }

        Long recipientId = recipient.getId();
        if (isOptedOut(recipientId, type)) {
            return Optional.empty();
        }

        String dedupeKey = scheduledDedupeKey(type, transactionId, dueAt);
        notificationRepository.insertScheduledNotificationIfAbsent(
                recipientId,
                recipient.getEmail(),
                transactionId,
                dedupeKey,
                type.name(),
                title,
                message,
                NotificationStatus.UNREAD.name(),
                LocalDateTime.now());

        Notification canonical = notificationRepository
                .findScheduledNotificationForUpdate(recipientId, dedupeKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Notification was not found after scheduled insert"));
        return Optional.of(canonical);
    }

    /**
     * Returns all visible notifications for the given user, newest first.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(User user) {
        requireUser(user);
        return notificationRepository
                .findByRecipientIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        user.getId(), NotificationStatus.SUPPRESSED)
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
     * existence is never disclosed. Suppressed rows are never exposed or
     * mutated, and an already-read row keeps its first read timestamp.
     */
    @Transactional
    public NotificationResponse markAsRead(Long id, User user) {
        requireUser(user);
        Notification n = notificationRepository.findByIdAndRecipientIdForUpdate(id, user.getId())
                .filter(notification -> notification.getStatus() != NotificationStatus.SUPPRESSED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found with id: " + id));

        if (n.getStatus() != NotificationStatus.READ) {
            n.setStatus(NotificationStatus.READ);
            n.setReadAt(LocalDateTime.now());
            n = notificationRepository.save(n);
        }
        return toResponse(n);
    }

    private static String scheduledDedupeKey(NotificationType type,
                                             Long transactionId,
                                             LocalDateTime dueAt) {
        return SCHEDULED_DEDUPE_PREFIX + type.name() + ":" + transactionId + ":"
                + dueAt.truncatedTo(ChronoUnit.MICROS).format(SCHEDULED_DUE_AT_FORMATTER);
    }

    private boolean isOptedOut(Long recipientId, NotificationType type) {
        return preferenceRepository.findByUserIdAndType(recipientId, type)
                .map(p -> !p.isEnabled())
                .orElse(false);
    }

    private void requireUser(User user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication is required");
        }
    }

    public NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getTransactionId(),
                n.getStatus(),
                n.getCreatedAt(),
                n.getReadAt(),
                n.getRecipientEmail()
        );
    }
}
