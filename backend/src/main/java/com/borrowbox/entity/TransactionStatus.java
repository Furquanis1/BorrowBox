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
 * RETURN_DISPUTED (V2.2.6) means the lender reported the returned item was NOT
 * received while the transaction was RETURN_REPORTED. Unlike HANDOVER_DISPUTED,
 * the AssetUnit STAYS BORROWED: the item's return is contested, so the unit is
 * not released back to AVAILABLE. The transaction is terminal and read-only;
 * return-dispute resolution is later-stage scope.
 *
 * DUE_SOON / OVERDUE are derived read-time conditions, never persisted states.
 * The same is true of the V2.5.2 derived condition `reservationExpired`, which
 * reports that a live state's reservation deadline has passed but has not yet
 * been swept.
 *
 * EXPIRED (V2.5.2) means the reservation window for a not-yet-picked-up item
 * elapsed. It is a terminal decision branch, reached from PENDING,
 * COUNTER_OFFERED, APPROVED or AWAITING_HANDOVER once the transaction's
 * reservationExpiresAt deadline has actually passed; the reserved AssetUnit is
 * released back to AVAILABLE by the same action. It does not apply to ACTIVE /
 * RETURN_INITIATED / RETURN_REPORTED (the item is in use, the unit is BORROWED)
 * nor to RETURN_DISPUTED (the unit is deliberately frozen), and it never
 * applies when reservationExpiresAt is NULL.
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
    HANDOVER_DISPUTED,
    RETURN_DISPUTED,
    EXPIRED
}