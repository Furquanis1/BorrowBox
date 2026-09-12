package com.borrowbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of a participant-sent conversation message. The service normalizes and
 * re-validates (trim, blank, length) after bean validation so the rules are
 * backend-authoritative regardless of API shape.
 */
public record TransactionMessageRequest(
        @NotBlank @Size(max = 1000) String body
) {
}