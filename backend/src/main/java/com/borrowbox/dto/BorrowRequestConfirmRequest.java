package com.borrowbox.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Request body for POST /api/borrow-requests/{id}/confirm.
 * The owner specifies a due date; the server handles the rest.
 */
public record BorrowRequestConfirmRequest(
        @NotNull LocalDateTime dueAt
) {
}
