package com.borrowbox.dto;

import com.borrowbox.entity.WebhookDeliveryStatus;

import java.time.LocalDateTime;

/**
 * Response DTO for webhook delivery records (V2.6).
 */
public record WebhookDeliveryResponse(
        Long id,
        Long webhookEndpointId,
        String eventId,
        String eventType,
        Long transactionId,
        WebhookDeliveryStatus status,
        Integer httpStatusCode,
        String responseBody,
        String errorMessage,
        Integer attemptNumber,
        LocalDateTime nextRetryAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
