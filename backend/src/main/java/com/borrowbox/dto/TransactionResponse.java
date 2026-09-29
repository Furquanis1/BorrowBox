package com.borrowbox.dto;

import com.borrowbox.entity.TransactionStatus;

import java.time.LocalDateTime;

/**
 * Transaction negotiation view. Physical-unit identifiers (AssetUnit IDs,
 * reserved_unit IDs) are NEVER exposed.
 *
 * V2.2.4: dueAt / originalDueAt / borrowerConfirmedAt are persisted;
 * dueSoon / overdue / handoverWindowOpen are derived read-time booleans.
 *
 * V2.2.5: extensionRequestedDueAt / extensionOfferedDueAt / extensionNote /
 * extensionRequestedAt are the single pending extension negotiation persisted
 * on the transaction; extensionRequestPending / extensionCounterPending are
 * derived read-time booleans (true only while ACTIVE).
 *
 * V2.2.6: returnDisputedAt is stamped by the backend clock when the lender
 * disputes the return (RETURN_DISPUTED).
 *
 * V2.5.2: reservationExpired is a derived read-time boolean (like dueSoon /
 * overdue) that is true only when the state is one of PENDING /
 * COUNTER_OFFERED / APPROVED / AWAITING_HANDOVER, the persisted
 * reservationExpiresAt deadline is non-null, and the server clock is past it.
 * A null deadline is never expired, and the flag is not persisted: it reports
 * that a sweep is still owed, not that the transaction has transitioned to
 * EXPIRED.
 *
 * <p>The deadline itself (transaction.reservationExpiresAt) is deliberately
 * NOT part of this record. It is an internal scheduling detail of the
 * expiry sweep, and exposing it would make the API contract include a
 * timestamp that no client needs and that would have to be kept in sync with
 * the sweep's behaviour.
 */
public record TransactionResponse(
        Long id,
        Long communityId,
        String communityName,
        Long listingId,
        Long assetId,
        String title,
        Long lenderId,
        String lenderName,
        Long borrowerId,
        String borrowerName,
        TransactionStatus state,
        String purpose,
        String borrowerNote,
        Integer requestedDurationDays,
        String counterPurpose,
        Integer counterDurationDays,
        String counterNote,
        LocalDateTime counterOfferedAt,
        String agreedPurpose,
        Integer agreedDurationDays,
        LocalDateTime agreedAt,
        String decisionNote,
        boolean reservationHeld,
        LocalDateTime startedAt,
        LocalDateTime dueAt,
        LocalDateTime originalDueAt,
        LocalDateTime borrowerConfirmedAt,
        LocalDateTime extensionRequestedDueAt,
        LocalDateTime extensionOfferedDueAt,
        String extensionNote,
        LocalDateTime extensionRequestedAt,
        boolean extensionRequestPending,
        boolean extensionCounterPending,
        boolean dueSoon,
        boolean overdue,
        boolean handoverWindowOpen,
        LocalDateTime returnDisputedAt,
        boolean reservationExpired,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}