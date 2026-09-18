package com.borrowbox.entity;

/**
 * V2.2.8 transaction event types.
 * Separate from TransactionMessage SYSTEM kinds — these are structured semantic events
 * with per-recipient delivery state.
 */
public enum TransactionEventType {
    // Request lifecycle
    REQUEST_APPROVED,
    REQUEST_REJECTED,
    REQUEST_CANCELLED,

    // Handover / loan lifecycle
    HANDOVER_SCHEDULED,
    LOAN_STARTED,
    HANDOVER_CONFIRMED,
    HANDOVER_DISPUTED,

    // Extensions
    EXTENSION_REQUESTED,
    EXTENSION_APPROVED,
    EXTENSION_REJECTED,
    EXTENSION_COUNTERED,
    EXTENSION_COUNTER_ACCEPTED,
    EXTENSION_COUNTER_REJECTED,

    // Return lifecycle
    RETURN_INITIATED,
    RETURN_REPORTED,
    LOAN_COMPLETED,
    RETURN_DISPUTED,

    // Waitlist
    WAITLIST_PROMOTED
}