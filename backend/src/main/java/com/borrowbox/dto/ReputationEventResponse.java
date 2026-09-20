package com.borrowbox.dto;

import com.borrowbox.entity.ReputationEvent;
import com.borrowbox.entity.ReputationEventType;
import com.borrowbox.entity.ReputationRole;

import java.time.LocalDateTime;

/**
 * V2.3.2 self-scoped reputation ledger row (ADR-020, ADR-021) — one immutable
 * outcome appended the moment a transaction reaches a reputation-bearing
 * terminal state Schweizer the authoritative backend clock.
 *
 * <p>A single semantic event may yield multiple ledger rows; this surface is
 * the per-user view of one row. Callers always read their OWN ledger — there
 * is no cross-user read surface.
 *
 * @param id            ledger row identity
 * @param eventType     the reputation-bearing semantic event kind
 * @param role          which participant perspective this row records
 * @param communityId   the community the outcome occurred in
 * @param communityName the community's name
 * @param transactionId the transaction whose terminal state produced the row
 * @param onTime        on-time outcome: true/false for a borrower; null for a
 *                      lender (lenders face no on-time deadline) and null for
 *                      a lender's LOAN_COMPLETED row
 * @param successful    whether the reputation-bearing outcome succeeded
 * @param occurredAt    authoritative server timestamp of the outcome
 */
public record ReputationEventResponse(
        Long id,
        ReputationEventType eventType,
        ReputationRole role,
        Long communityId,
        String communityName,
        Long transactionId,
        Boolean onTime,
        Boolean successful,
        LocalDateTime occurredAt
) {

    public static ReputationEventResponse from(ReputationEvent event) {
        return new ReputationEventResponse(
                event.getId(),
                event.getEventType(),
                event.getRole(),
                event.getCommunity() != null ? event.getCommunity().getId() : null,
                event.getCommunity() != null ? event.getCommunity().getName() : null,
                event.getTransaction() != null ? event.getTransaction().getId() : null,
                event.getOnTime(),
                event.isSuccessful(),
                event.getOccurredAt()
        );
    }
}
