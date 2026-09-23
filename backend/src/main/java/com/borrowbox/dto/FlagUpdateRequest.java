package com.borrowbox.dto;

import com.borrowbox.entity.FlagStatus;

/**
 * V2.4.2 PATCH semantics: every field is optional and updates only what is
 * present. {@code clearAssignee = true} unassigns the flag; sending both
 * clearAssignee and assigneeId is rejected by the service.
 */
public record FlagUpdateRequest(
        FlagStatus status,
        Long assigneeId,
        Boolean clearAssignee,
        String note
) {
}