package com.borrowbox.dto;

import java.math.BigDecimal;
import java.util.Map;

public record CommunityJoinRequest(
        BigDecimal latitude,
        BigDecimal longitude,
        Map<String, Object> contextMetadata
) {
}