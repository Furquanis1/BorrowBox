package com.borrowbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Structured manager dispute-resolution input.
 *
 * @param outcome         mandatory manager decision
 * @param resolutionNote  mandatory durable explanation written to the SYSTEM
 *                        conversation entry and the semantic event payload
 * @param unitCondition   optional physical finding; defaults to INTACT
 * @param freeUnit        optional release flag; defaults to true. A false value
 *                        keeps an intact unit out of circulation as NOT_AVAILABLE.
 */
public record DisputeResolutionRequest(
        @NotNull DisputeResolutionOutcome outcome,
        @NotBlank @Size(max = 1000) String resolutionNote,
        DisputeUnitCondition unitCondition,
        Boolean freeUnit
) {
}
