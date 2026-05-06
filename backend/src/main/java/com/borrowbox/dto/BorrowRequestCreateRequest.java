package com.borrowbox.dto;

import jakarta.validation.constraints.NotNull;

public record BorrowRequestCreateRequest(
        @NotNull Long itemId,
        @NotNull Long requestedByUserId,
        String message
) {
}