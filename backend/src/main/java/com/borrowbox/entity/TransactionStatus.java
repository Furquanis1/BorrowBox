package com.borrowbox.entity;

/**
 * Transaction lifecycle states (V2.2.1 negotiation + V2.2.2 loan lifecycle).
 *
 * RESERVED is deliberately NOT a transaction state: it is a physical
 * AssetUnit status only.
 */
public enum TransactionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COUNTER_OFFERED,
    CANCELLED,
    AWAITING_HANDOVER,
    ACTIVE,
    RETURN_INITIATED,
    COMPLETED
}