package com.borrowbox.dto;

import com.borrowbox.entity.ListingStatus;

import java.time.LocalDateTime;

/**
 * Aggregate listing view. One row per (asset, community) listing.
 *
 * Availability counts are always derived server-side from the shared
 * AssetUnit pool of the asset. AssetUnit IDs are never exposed.
 *
 * V2.2.7: waitingCount is the count of WAITING waitlist entries for the
 * Asset, aggregated across all communities that list it (the queue is
 * per-Asset). It never exposes waiter identities — owners see a count only.
 */
public record ListingResponse(
        Long id,
        Long assetId,
        Long communityId,
        String communityName,
        ListingStatus listingStatus,
        LocalDateTime listedAt,
        String title,
        String description,
        Long categoryId,
        String categoryName,
        long totalUnits,
        long availableUnits,
        long borrowedUnits,
        long waitingCount
) {
}