package com.borrowbox.dto;

import com.borrowbox.entity.TransactionEventDeliveryStatus;
import com.borrowbox.entity.TransactionEventType;

import java.time.LocalDateTime;

/**
 * V2.2.8 transaction event delivery response.
 * Combines event data with per-recipient delivery state.
 */
public record TransactionEventDeliveryResponse(
        Long deliveryId,
        Long eventId,
        TransactionEventType eventType,
        TransactionEventDeliveryStatus status,
        Long transactionId,
        Long assetId,
        String assetTitle,
        Long communityId,
        String communityName,
        Long actorId,
        String actorName,
        String payload,
        LocalDateTime eventCreatedAt,
        LocalDateTime readAt,
        LocalDateTime dismissedAt
) {
}