package com.borrowbox.repository;

import com.borrowbox.entity.WebhookEndpoint;
import com.borrowbox.entity.WebhookEndpointStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link WebhookEndpoint} rows (V2.6).
 *
 * All lookups are scoped by user so a user can only manage their own
 * webhook endpoints.
 */
@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {

    List<WebhookEndpoint> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WebhookEndpoint> findByIdAndUserId(Long id, Long userId);

    List<WebhookEndpoint> findByUserIdAndStatus(Long userId, WebhookEndpointStatus status);
}
