package com.borrowbox.dto;

import com.borrowbox.entity.TransactionEventType;

import java.time.LocalDateTime;

/**
 * V2.2.8 transaction event response.
 */
public record TransactionEventResponse(
        Long id,
        Long transactionId,
        TransactionEventType eventType,
        Long actorId,
        String actorName,
        String payload,
        LocalDateTime createdAt
) {
}