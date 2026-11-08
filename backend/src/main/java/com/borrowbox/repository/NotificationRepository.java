package com.borrowbox.repository;

import com.borrowbox.entity.Notification;
import com.borrowbox.entity.NotificationStatus;
import com.borrowbox.entity.NotificationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Notification} rows (V2.6).
 *
 * All lookups are scoped by recipient so a user can never see another user's
 * notifications.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdAndStatusNotOrderByCreatedAtDescIdDesc(
            Long recipientId, NotificationStatus excludedStatus);

    long countByRecipientIdAndStatus(Long recipientId, NotificationStatus status);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO notifications
                (recipient_id, recipient_email, transaction_id, source_event_id, dedupe_key,
                 type, title, message, status, created_at, updated_at)
            VALUES
                (:recipientId, :recipientEmail, :transactionId, :sourceEventId, NULL,
                 :type, :title, :message, :status, :now, :now)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertEventNotificationIfAbsent(@Param("recipientId") Long recipientId,
                                        @Param("recipientEmail") String recipientEmail,
                                        @Param("transactionId") Long transactionId,
                                        @Param("sourceEventId") Long sourceEventId,
                                        @Param("type") String type,
                                        @Param("title") String title,
                                        @Param("message") String message,
                                        @Param("status") String status,
                                        @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO notifications
                (recipient_id, recipient_email, transaction_id, source_event_id, dedupe_key,
                 type, title, message, status, created_at, updated_at)
            VALUES
                (:recipientId, :recipientEmail, :transactionId, NULL, :dedupeKey,
                 :type, :title, :message, :status, :now, :now)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertScheduledNotificationIfAbsent(@Param("recipientId") Long recipientId,
                                            @Param("recipientEmail") String recipientEmail,
                                            @Param("transactionId") Long transactionId,
                                            @Param("dedupeKey") String dedupeKey,
                                            @Param("type") String type,
                                            @Param("title") String title,
                                            @Param("message") String message,
                                            @Param("status") String status,
                                            @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n from Notification n
            where n.recipient.id = :recipientId
              and n.sourceEventId = :sourceEventId
              and n.type = :type
            """)
    Optional<Notification> findEventNotificationForUpdate(
            @Param("recipientId") Long recipientId,
            @Param("sourceEventId") Long sourceEventId,
            @Param("type") NotificationType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n from Notification n
            where n.recipient.id = :recipientId
              and n.dedupeKey = :dedupeKey
            """)
    Optional<Notification> findScheduledNotificationForUpdate(
            @Param("recipientId") Long recipientId,
            @Param("dedupeKey") String dedupeKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n from Notification n
            where n.id = :id
              and n.recipient.id = :recipientId
            """)
    Optional<Notification> findByIdAndRecipientIdForUpdate(
            @Param("id") Long id,
            @Param("recipientId") Long recipientId);
}
