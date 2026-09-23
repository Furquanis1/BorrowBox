package com.borrowbox.dto;

import com.borrowbox.entity.FlagType;
import jakarta.validation.constraints.NotNull;

/**
 * V2.4.2 create-flag request. The reporter is always the authenticated
 * acting manager; no reporter id is accepted here. transactionId is optional
 * (a null value opens a manual flag not tied to a loan).
 */
public record FlagCreateRequest(
        @NotNull(message = "A flag type is required")
        FlagType flagType,
        Long transactionId,
        String note
) {
}