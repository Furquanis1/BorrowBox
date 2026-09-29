package com.borrowbox.entity;

/**
 * V2.2.8 transaction event types.
 * Separate from TransactionMessage SYSTEM kinds — these are structured semantic events
 * with per-recipient delivery state.
 *
 * V2.5.2 adds EXPIRED: the reservation window elapsed before pickup. It is
 * addressed to the same participants as the request-lifecycle events because the
 * borrower loses the held unit to it, so recipient resolution must be explicit.
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

    // Reservation expiry
    EXPIRED,

    // Waitlist
    WAITLIST_PROMOTED
}