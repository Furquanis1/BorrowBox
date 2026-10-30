package com.borrowbox.repository;

import com.borrowbox.entity.NotificationPreference;
import com.borrowbox.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link NotificationPreference} rows (V2.6).
 *
 * All lookups are scoped by user so each user manages their own preferences.
 */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByUserIdOrderByTypeAsc(Long userId);

    Optional<NotificationPreference> findByUserIdAndType(Long userId, NotificationType type);
}
