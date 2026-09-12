package com.borrowbox.entity;

/**
 * Transaction lifecycle states (V2.2.1 negotiation + V2.2.2 loan lifecycle +
 * V2.2.3 return phases).
 *
 * Final lifecycle:
 *   PENDING → APPROVED → AWAITING_HANDOVER → ACTIVE → RETURN_INITIATED
 *   → RETURN_REPORTED → COMPLETED
 *
 * RETURN_INITIATED means the borrower has started the return process and is
 * coordinating the physical return. RETURN_REPORTED means the borrower has
 * explicitly reported the item was handed back; only a lender receipt confirms
 * COMPLETED. REJECTED / CANCELLED are the terminal decision branches.
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
    RETURN_REPORTED,
    COMPLETED
}