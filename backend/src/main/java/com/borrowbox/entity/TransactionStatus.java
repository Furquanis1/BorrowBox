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
 * COMPLETED. REJECTED / CANCELLED and, from V2.2.4, HANDOVER_DISPUTED are the
 * terminal decision branches.
 *
 * HANDOVER_DISPUTED (V2.2.4) means the borrower disputed non-receipt within
 * the 30-minute handover confirmation window; the borrowed AssetUnit is
 * immediately released back to AVAILABLE. It has no forward transitions yet:
 * community-manager resolution is later-stage scope.
 *
 * DUE_SOON / OVERDUE are derived read-time conditions, never persisted states.
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
    COMPLETED,
    HANDOVER_DISPUTED
}