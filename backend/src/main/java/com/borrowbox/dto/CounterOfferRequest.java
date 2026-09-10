package com.borrowbox.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CounterOfferRequest(
        String purpose,
        @NotNull @Min(1) @Max(30) Integer requestedDurationDays,
        String note
) {
}