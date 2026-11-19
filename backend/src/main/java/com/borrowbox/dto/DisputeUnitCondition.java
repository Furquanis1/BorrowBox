package com.borrowbox.dto;

/**
 * Manager's finding about the physical unit at dispute resolution time.
 *
 * <p>This is an API-level finding, not an {@code AssetUnitStatus}: it drives the
 * authoritative unit status transition while keeping the manager decision
 * vocabulary independent of inventory internals.
 */
public enum DisputeUnitCondition {
    INTACT,
    DAMAGED,
    LOST
}
