package com.borrowbox.dto;

import com.borrowbox.entity.CommunityRuleType;
import com.borrowbox.entity.CommunityStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record CommunityRuleResponse(
        Long id,
        Long communityId,
        CommunityRuleType ruleType,
        Map<String, Object> value,
        CommunityStatus status,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}