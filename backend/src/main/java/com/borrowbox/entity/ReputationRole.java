package com.borrowbox.entity;

/**
 * V2.3.2 reputation identity — who the row belongs to within one semantic event.
 *
 * <p>ADR-020/ADR-021: a single reputation-bearing semantic event may have
 * multiple derivable ledger rows. The role discriminates the accountability
 * perspective per participant:
 *
 * <ul>
 *   <li>{@code BORROWER} — faces the on-time return deadline; owns an
 *       on-time/late determination and a success flag.</li>
 *   <li>{@code LENDER} — faces no on-time deadline; {@code onTime} stays
 *       null because there is no lender-side deadline to be late against.</li>
 * </ul>
 */
public enum ReputationRole {

    BORROWER,
    LENDER
}
