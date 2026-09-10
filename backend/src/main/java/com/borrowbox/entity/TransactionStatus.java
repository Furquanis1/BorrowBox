package com.borrowbox.entity;

/**
 * V2.2.1 negotiation states. APPROVED is effectively terminal until the V2.2.2
 * handover slice adds AWAITING_HANDOVER. RESERVED is deliberately NOT a
 * transaction state: it is a physical AssetUnit status only.
 */
public enum TransactionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COUNTER_OFFERED,
    CANCELLED
}