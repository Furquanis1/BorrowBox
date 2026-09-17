package com.borrowbox.entity;

/**
 * The four live evidence moments defined for a normal transaction (ADR-008).
 *
 *   LENDER_PRE_LENDING       - lender live photo immediately before lending
 *   LENDER_HANDOVER          - lender live handover photo: item + recipient
 *   BORROWER_PRE_RETURN      - borrower live photo immediately before returning
 *   BORROWER_RETURN_HANDOVER - borrower live return-handover photo: item + receiver
 *
 * V2.2.6 implements the return-side moments only (BORROWER_PRE_RETURN and
 * BORROWER_RETURN_HANDOVER). The borrow-side moments are deferred.
 */
public enum EvidenceType {
    LENDER_PRE_LENDING,
    LENDER_HANDOVER,
    BORROWER_PRE_RETURN,
    BORROWER_RETURN_HANDOVER
}