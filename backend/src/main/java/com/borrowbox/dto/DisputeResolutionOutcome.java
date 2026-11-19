package com.borrowbox.dto;

/**
 * Manager decisions available for a disputed transaction.
 *
 * <p>The values are deliberately explicit rather than a generic APPROVE/REJECT
 * pair: a manager can close a dispute in favour of either participant or return
 * the transaction to the active loan flow when the dispute was a
 * misunderstanding.
 */
public enum DisputeResolutionOutcome {
    FAVOR_LENDER,
    FAVOR_BORROWER,
    REOPEN
}
