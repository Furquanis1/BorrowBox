package com.borrowbox.dto;

import com.borrowbox.entity.WaitlistStatus;

import java.time.LocalDateTime;

/**
 * V2.2.7 waitlist response. Position is derived on read (never persisted) and
 * is only meaningful for WAITING entries. Waiter identities are never exposed
 * to owners: a listing only surfaces the per-asset WAITING count.
 */
public record WaitlistEntryResponse(
        Long id,
        Long assetId,
        String assetTitle,
        Long listingId,
        Long communityId,
        String communityName,
        Long borrowerId,
        String purpose,
        Integer requestedDurationDays,
        WaitlistStatus status,
        long position,
        LocalDateTime createdAt,
        LocalDateTime promotedAt
) {
}