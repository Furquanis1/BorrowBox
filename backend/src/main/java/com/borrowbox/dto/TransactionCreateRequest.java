package com.borrowbox.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransactionCreateRequest(
        @NotNull Long listingId,
        @NotBlank String purpose,
        @NotNull @Min(1) @Max(30) Integer requestedDurationDays
) {
}