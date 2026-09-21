package com.borrowbox.entity;

/**
 * V2.3.2 reputation event kinds (ADR-020, ADR-021). Append-only semantic
 * labels recorded on the reputation ledger when a transaction reaches a
 * reputation-bearing terminal state.
 *
 * <ul>
 *   <li>{@code LOAN_COMPLETED} — confirmed by the lender ({@code confirmReturn});
 *       appends TWO ledger rows atomically in the same database transaction:
 *       borrower (role=BORROWER) and lender (role=LENDER), both
 *       successful=true.</li>
 *   <li>{@code RETURN_DISPUTED} — lender disputes the claimed return
 *       ({@code disputeReturn}); appends ONE borrower row (role=BORROWER,
 *       successful=false, on_time=false).</li>
 * </ul>
 *
 * These are reputation-bearing terminal states only. Non-terminal or
 * mid-handover states never emit reputation rows.
 */
public enum ReputationEventType {

    LOAN_COMPLETED,
    RETURN_DISPUTED
}
