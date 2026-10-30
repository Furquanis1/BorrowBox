package com.borrowbox.dto;

import com.borrowbox.entity.NotificationStatus;
import com.borrowbox.entity.NotificationType;

import java.time.LocalDateTime;

/**
 * Notification view returned to the recipient.
 *
 * The physical unit ID and any transaction-internal IDs beyond the transaction
 * itself are intentionally not exposed.
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long transactionId,
        NotificationStatus status,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        String recipientEmail
) {
}
