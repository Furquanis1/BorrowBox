package com.borrowbox.entity;

/**
 * V2.2.7 waitlist lifecycle statuses.
 *
 * <ul>
 *   <li>WAITING — live entry, eligible for promotion.</li>
 *   <li>PROMOTED — promotion succeeded: a normal PENDING transaction was
 *       created and this entry is terminal audit (promotedAt stamped by the
 *       server clock).</li>
 *   <li>LEFT — used only when promotion skips an ineligible head (unlisted,
 *       archived, no longer an active member, became owner). Permanently gone;
 *       never revived. A voluntary leave hard-deletes the WAITING row instead
 *       of marking it LEFT.</li>
 * </ul>
 */
public enum WaitlistStatus {
    WAITING,
    PROMOTED,
    LEFT
}