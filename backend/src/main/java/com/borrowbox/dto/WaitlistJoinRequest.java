package com.borrowbox.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * V2.2.7 join-the-waitlist request. Mirrors the normal request form (purpose +
 * requested duration) because a promotion directly seeds a normal PENDING
 * transaction with the same fields.
 */
public record WaitlistJoinRequest(
        @NotBlank String purpose,
        @NotNull @Min(1) @Max(30) Integer requestedDurationDays
) {
}