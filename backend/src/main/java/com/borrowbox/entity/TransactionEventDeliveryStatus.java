package com.borrowbox.entity;

/**
 * V2.2.8 delivery status for transaction events.
 * UNREAD -> READ (user opens)
 * UNREAD -> DISMISSED (user clicks Later/X)
 * DISMISSED -> READ (user later opens from panel)
 */
public enum TransactionEventDeliveryStatus {
    UNREAD,
    READ,
    DISMISSED
}