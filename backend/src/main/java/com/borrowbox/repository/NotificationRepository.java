package com.borrowbox.repository;

import com.borrowbox.entity.Notification;
import com.borrowbox.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link Notification} rows (V2.6).
 *
 * All lookups are scoped by recipient so a user can never see another user's
 * notifications.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId);

    long countByRecipientIdAndStatus(Long recipientId, NotificationStatus status);
}
