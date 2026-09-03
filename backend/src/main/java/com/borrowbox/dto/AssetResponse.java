package com.borrowbox.dto;

import com.borrowbox.entity.AssetStatus;

public record AssetResponse(
        Long id,
        String title,
        String description,
        Long categoryId,
        String categoryName,
        AssetStatus status,
        long totalUnits,
        long availableUnits,
        long borrowedUnits
) {
}
