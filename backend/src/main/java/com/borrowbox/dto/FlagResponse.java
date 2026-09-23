package com.borrowbox.dto;

import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;

import java.time.LocalDateTime;

/**
 * V2.4.2 flag read model. Core incident facts (community, transaction,
 * flagType, reporter, occurredAt, createdAt) are immutable; only the workflow
 * fields (status, assignee, note, updatedAt) may change.
 */
public record FlagResponse(
        Long id,
        Long communityId,
        Long transactionId,
        FlagType flagType,
        FlagStatus status,
        Long reporterId,
        Long assigneeId,
        String assigneeName,
        String note,
        LocalDateTime occurredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FlagResponse from(com.borrowbox.entity.Flag flag) {
        Long communityId = flag.getCommunity() != null ? flag.getCommunity().getId() : null;
        Long transactionId = flag.getTransaction() != null ? flag.getTransaction().getId() : null;
        Long reporterId = flag.getReporter() != null ? flag.getReporter().getId() : null;
        Long assigneeId = flag.getAssignee() != null ? flag.getAssignee().getId() : null;
        String assigneeName = flag.getAssignee() != null ? flag.getAssignee().getFullName() : null;
        return new FlagResponse(
                flag.getId(),
                communityId,
                transactionId,
                flag.getFlagType(),
                flag.getStatus(),
                reporterId,
                assigneeId,
                assigneeName,
                flag.getNote(),
                flag.getOccurredAt(),
                flag.getCreatedAt(),
                flag.getUpdatedAt()
        );
    }
}