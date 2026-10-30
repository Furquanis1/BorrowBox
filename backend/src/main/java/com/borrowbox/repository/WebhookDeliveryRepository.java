package com.borrowbox.repository;

import com.borrowbox.entity.WebhookDelivery;
import com.borrowbox.entity.WebhookDeliveryStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link WebhookDelivery} rows (V2.6).
 *
 * All lookups are scoped by endpoint so a user can only see their own
 * delivery records.
 */
@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(Long endpointId);

    Optional<WebhookDelivery> findByIdAndEndpointId(Long id, Long endpointId);

    List<WebhookDelivery> findByEndpointIdAndStatusOrderByCreatedAtDesc(Long endpointId,
            WebhookDeliveryStatus status);

    List<WebhookDelivery> findByStatusAndNextRetryAtBefore(WebhookDeliveryStatus status,
            LocalDateTime now);

    boolean existsByEventIdAndEndpointId(String eventId, Long endpointId);

    @EntityGraph(attributePaths = "endpoint")
    @Query("SELECT d FROM WebhookDelivery d WHERE d.id IN :ids")
    List<WebhookDelivery> findAllWithEndpointByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Atomically claims due pending rows by transitioning them to
     * {@code CLAIMED}. The update is the claim: only the worker whose update
     * affects a row may fetch it by token and make the HTTP request.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "UPDATE webhook_deliveries "
            + "SET status = 'CLAIMED', claim_token = :claimToken, claimed_at = :now, "
            + "claim_expires_at = :leaseExpiresAt "
            + "WHERE status = 'PENDING' AND next_retry_at <= :now "
            + "ORDER BY next_retry_at, id LIMIT :batchSize",
            nativeQuery = true)
    int claimPendingDeliveries(@Param("claimToken") String claimToken,
            @Param("now") LocalDateTime now,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
            @Param("batchSize") int batchSize);

    /**
     * Atomically claims a known set of newly created deliveries. Rows that
     * another worker already changed out of {@code PENDING} are left untouched.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE WebhookDelivery d SET d.status = :claimedStatus, d.claimToken = :claimToken, "
            + "d.claimedAt = :now, d.claimExpiresAt = :leaseExpiresAt "
            + "WHERE d.id IN :ids AND d.status = :pendingStatus AND d.nextRetryAt <= :now")
    int claimPendingDeliveriesByIdIn(@Param("ids") Collection<Long> ids,
            @Param("pendingStatus") WebhookDeliveryStatus pendingStatus,
            @Param("claimedStatus") WebhookDeliveryStatus claimedStatus,
            @Param("claimToken") String claimToken,
            @Param("now") LocalDateTime now,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    /**
     * Returns expired claims to {@code PENDING} so another worker can retry
     * them after a crash or restart.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE WebhookDelivery d SET d.status = :pendingStatus, d.claimToken = null, "
            + "d.claimedAt = null, d.claimExpiresAt = null "
            + "WHERE d.status = :claimedStatus "
            + "AND (d.claimExpiresAt IS NULL OR d.claimExpiresAt < :now)")
    int releaseExpiredClaims(@Param("claimedStatus") WebhookDeliveryStatus claimedStatus,
            @Param("pendingStatus") WebhookDeliveryStatus pendingStatus,
            @Param("now") LocalDateTime now);

    @Transactional(readOnly = true)
    @EntityGraph(attributePaths = "endpoint")
    List<WebhookDelivery> findAllByClaimTokenAndStatusOrderByIdAsc(String claimToken,
            WebhookDeliveryStatus status);
}
