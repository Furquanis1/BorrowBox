package com.borrowbox.entity;

/**
 * V2.6: Delivery status for webhook notifications.
 * PENDING -> CLAIMED -> SENT / PENDING (retry) / FAILED (terminal)
 */
public enum WebhookDeliveryStatus {
    PENDING,
    CLAIMED,
    SENT,
    FAILED
}
