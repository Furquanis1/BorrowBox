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
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}