package com.borrowbox.service;

import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.ReputationEvent;
import com.borrowbox.entity.ReputationEventType;
import com.borrowbox.entity.ReputationRole;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.ReputationEventRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * V2.3.2 reputation-ledger writer (ADR-020, ADR-021) — the ONE authoritative
 * place that appends reputation rows. Append-only: no update, no delete, no
 * migration of an existing row, ever.
 *
 * <p>Two entry points are recorded the instant a transaction reaches a
 * reputation-bearing terminal state, stamped with the authoritative server
 * clock, and committed in the SAME transaction as the state mutation that
 * derives them:
 *
 * <ul>
 *   <li>{@link #recordLoanCompleted(Transaction)} — {@code confirmReturn()}
 *       appends TWO rows atomically: borrower (role=BORROWER, successful=true,
 *       onTime = completedAt &lt;= final dueAt) and lender (role=LENDER,
 *       successful=true, onTime = null — a lender faces no on-time return
 *       deadline).</li>
 *   <li>{@link #recordReturnDisputed(Transaction)} — {@code disputeReturn()}
 *       appends ONE borrower row (role=BORROWER, successful=false,
 *       onTime = false).</li>
 * </ul>
 *
 * <p>Idempotency / self-healing: every append is preceded by an existence
 * check ({@link ReputationEventRepository#existsByTransactionIdAndUserIdAndEventType})
 * and backed by a UNIQUE (transaction_id, user_id, event_type) constraint
 * (ADR-020), so replaying a transition or re-running the seed's self-healing
 * reconcile can NEVER duplicate a row.
 */
@Service
public class ReputationEventService {

    private final ReputationEventRepository reputationEventRepository;
    private final MembershipRepository membershipRepository;

    public ReputationEventService(ReputationEventRepository reputationEventRepository,
                                  MembershipRepository membershipRepository) {
        this.reputationEventRepository = reputationEventRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Appends BOTH reputation rows for a completed loan, atomically, stamped
     * with the authoritative completedAt.
     *
     * <p>Must run in the same transaction as the COMPLETED state mutation
     * (Propagation.MANDATORY): if the state change commits, the ledger rows
     * commit with it; if it rolls back, the ledger rows vanish with it. There
     * is never a state without its ledger.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLoanCompleted(Transaction txn) {
        User borrower = txn.getBorrower();
        User lender = txn.getLender();
        LocalDateTime completedAt = txn.getCompletedAt();
        LocalDateTime finalDueAt = txn.getDueAt();
        Long txnId = txn.getId();

        boolean borrowerOnTime = completedAt != null && finalDueAt != null
                && !completedAt.isAfter(finalDueAt);

        appendIfMissing(txn, borrower, ReputationRole.BORROWER, ReputationEventType.LOAN_COMPLETED,
                true, borrowerOnTime, completedAt);
        appendIfMissing(txn, lender, ReputationRole.LENDER, ReputationEventType.LOAN_COMPLETED,
                true, null, completedAt);
    }

    /**
     * Appends the ONE borrower reputation row for a disputed return, stamped
     * with the authoritative returnDisputedAt. Same MANDATORY-transaction
     * guarantee as {@link #recordLoanCompleted(Transaction)}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReturnDisputed(Transaction txn) {
        User borrower = txn.getBorrower();
        LocalDateTime returnDisputedAt = txn.getReturnDisputedAt();
        Long txnId = txn.getId();

        appendIfMissing(txn, borrower, ReputationRole.BORROWER, ReputationEventType.RETURN_DISPUTED,
                false, false, returnDisputedAt);
    }

    /**
     * Appends one immutable ledger row unless the identical outcome already
     * exists (existence check FIRST, then insert — the UNIQUE constraint is
     * the final door). Handles a null participant (e.g. legacy rows) by
     * skipping silently so a partial/legacy transaction can never block a
     * sibling row.
     */
    private void appendIfMissing(Transaction txn, User user,
                                 ReputationRole role, ReputationEventType eventType,
                                 boolean successful, Boolean onTime, LocalDateTime occurredAt) {
        if (user == null || occurredAt == null) {
            return;
        }
        Long txnId = txn.getId();
        Long userId = user.getId();
        if (reputationEventRepository.existsByTransactionIdAndUserIdAndEventType(txnId, userId, eventType)) {
            return;
        }

        ReputationEvent event = new ReputationEvent();
        event.setUser(user);
        event.setCommunity(txn.getCommunity());
        event.setTransaction(txn);
        event.setEventType(eventType);
        event.setRole(role);
        event.setSuccessful(successful);
        event.setOnTime(onTime);
        event.setOccurredAt(occurredAt);
        reputationEventRepository.save(event);
    }

    /**
     * Self-scoped ledger surface for the authenticated user, newest first.
     * Optionally narrowed to one community the caller is an ACTIVE member of.
     */
    @Transactional(readOnly = true)
    public List<ReputationEvent> listForUser(Long userId, Long communityId) {
        if (userId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (communityId != null) {
            membershipRepository
                    .findByUserIdAndCommunityIdAndStatus(userId, communityId, MembershipStatus.ACTIVE)
                    .orElseThrow(() -> new AccessDeniedException(
                            "You are not an active member of this community"));
            return reputationEventRepository
                    .findByUserIdAndCommunityIdOrderByOccurredAtDesc(userId, communityId);
        }
        return reputationEventRepository.findByUserIdOrderByOccurredAtDesc(userId);
    }

    /**
     * Scoped single-row lookup used by the ledger surface when narrowing by a
     * transaction is required — returns empty when nothing is recorded, never
     * throws.
     */
    @Transactional(readOnly = true)
    public Optional<ReputationEvent> findByTransactionAndUser(Long transactionId, Long userId,
                                                               ReputationEventType eventType) {
        return reputationEventRepository
                .findByTransactionIdAndUserIdAndEventType(transactionId, userId, eventType);
    }
}
