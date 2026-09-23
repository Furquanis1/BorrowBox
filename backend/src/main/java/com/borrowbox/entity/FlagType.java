package com.borrowbox.entity;

/**
 * V2.4.1 community health + moderation: the kind of incident a {@link Flag}
 * records. Manual flags are opened by a community manager; the remaining types
 * describe structural incidents on a transaction (overdue loan, disputed
 * handover/return, missing evidence) for later review.
 */
public enum FlagType {
    OVERDUE,
    HANDOVER_DISPUTED,
    RETURN_DISPUTED,
    EVIDENCE_ISSUE,
    MANUAL
}