package com.borrowbox.dto;

import com.borrowbox.entity.WebhookEndpointStatus;

import java.time.LocalDateTime;

/**
 * Response DTO for webhook endpoint (V2.6).
 *
 * The secret token is intentionally NOT included - it is only returned once
 * at creation time and never again.
 */
public record WebhookEndpointResponse(
        Long id,
        String name,
        String url,
        WebhookEndpointStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
