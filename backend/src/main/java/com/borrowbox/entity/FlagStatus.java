package com.borrowbox.entity;

/**
 * V2.4.1 community health + moderation: the moderation workflow state of a
 * {@link Flag}. A flag is born OPEN and moves through REVIEWED to either
 * RESOLVED or DISMISSED. The default on creation is OPEN.
 */
public enum FlagStatus {
    OPEN,
    REVIEWED,
    RESOLVED,
    DISMISSED
}