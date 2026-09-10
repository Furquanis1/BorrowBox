package com.borrowbox.dto;

import com.borrowbox.entity.TransactionStatus;

import java.time.LocalDateTime;

/**
 * Transaction negotiation view. Physical-unit identifiers (AssetUnit IDs,
 * reserved_unit IDs) are NEVER exposed.
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}