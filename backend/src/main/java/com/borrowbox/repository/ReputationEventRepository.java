package com.borrowbox.repository;

import com.borrowbox.entity.ReputationEvent;
import com.borrowbox.entity.ReputationEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * V2.3.2 reputation ledger repository (ADR-020, ADR-021).
 *
 * <p>The ledger is append-only: reputation rows are never edited or deleted.
 * Idempotency is enforced jointly by the UNIQUE(transaction_id, user_id,
 * event_type) schema constraint AND an existence check before insert — so a
 * replayed confirmReturn/disputeReturn or a re-run self-healing reconcile can
 * never duplicate a row.
 *
 * <p>All reads are self-scoped: the authenticated user's own events, newest
 * first. Community filtering is applied by the service only after confirming
 * the caller is (or was at the time) a reputation-bearing participant; the
 * ledger itself never exposes another user's rows.
 */
public interface ReputationEventRepository extends JpaRepository<ReputationEvent, Long> {

    /** Idempotency guard: did this (user, transaction, event-type) outcome already get recorded? */
    boolean existsByTransactionIdAndUserIdAndEventType(Long transactionId, Long userId, ReputationEventType eventType);

    /** Fetch one ledger row by its semantic identity (used by reconcile, never duplicated). */
    Optional<ReputationEvent> findByTransactionIdAndUserIdAndEventType(Long transactionId, Long userId,
                                                                       ReputationEventType eventType);

    /** Self-scoped global ledger for the authenticated user, newest first. */
    List<ReputationEvent> findByUserIdOrderByOccurredAtDesc(Long userId);

    /** Self-scoped ledger within one community the user participated in, newest first. */
    List<ReputationEvent> findByUserIdAndCommunityIdOrderByOccurredAtDesc(Long userId, Long communityIdprop);
}
