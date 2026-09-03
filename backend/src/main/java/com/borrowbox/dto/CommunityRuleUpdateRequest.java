package com.borrowbox.dto;

import java.util.Map;

public record CommunityRuleUpdateRequest(
        Map<String, Object> value,
        Boolean active
) {
}