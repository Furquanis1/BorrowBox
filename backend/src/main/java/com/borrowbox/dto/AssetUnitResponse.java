package com.borrowbox.dto;

import com.borrowbox.entity.AssetUnitStatus;

public record AssetUnitResponse(
        Long id,
        String unitIdentifier,
        AssetUnitStatus status
) {
}
