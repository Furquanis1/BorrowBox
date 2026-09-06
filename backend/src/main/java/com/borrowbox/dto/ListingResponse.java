package com.borrowbox.dto;

import com.borrowbox.entity.ListingStatus;

import java.time.LocalDateTime;

/**
 * Aggregate listing view. One row per (asset, community) listing.
 *
 * Availability counts are always derived server-side from the shared
 * AssetUnit pool of the asset. AssetUnit IDs are never exposed.
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
        long borrowedUnits
) {
}