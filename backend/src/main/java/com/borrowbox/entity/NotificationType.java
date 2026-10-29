package com.borrowbox.entity;

/**
 * Notification type (V2.6). Mirrors transaction lifecycle events that warrant
 * telling the affected participant something happened.
 */
public enum NotificationType {
    LOAN_APPROVED,
    LOAN_REJECTED,
    LOAN_CANCELLED,
    HANDOVER_SCHEDULED,
    LOAN_STARTED,
    RECEIPT_CONFIRMED,
    RETURN_INITIATED,
    RETURN_HANDOVER_REPORTED,
    RETURN_DISPUTED,
    LOAN_COMPLETED,
    EXTENSION_REQUESTED,
    EXTENSION_APPROVED,
    EXTENSION_REJECTED,
    EXTENSION_COUNTERED,
    EXTENSION_COUNTER_ACCEPTED,
    EXTENSION_COUNTER_REJECTED
}
