package com.borrowbox.dto;

import com.borrowbox.entity.CommunityRuleType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CommunityRuleRequest(
        @NotNull CommunityRuleType ruleType,
        Map<String, Object> value
) {
}