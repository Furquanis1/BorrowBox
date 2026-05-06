package com.borrowbox.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BorrowRecordCreateRequest(
        @NotNull Long borrowRequestId,
        @NotNull Long itemId,
        @NotNull Long borrowedByUserId,
        @NotNull LocalDateTime borrowedAt,
        @NotNull LocalDateTime dueAt
) {
}