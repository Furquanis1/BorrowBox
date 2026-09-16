package com.borrowbox.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * V2.2.5 loan extension negotiation input. newDueAt is an absolute server-naive
 * LocalDateTime (ISO-8601) strictly after the current due date and the server
 * clock, and no more than 30 days after the current due date. The note is
 * optional free text surfaced in the conversation timeline context.
 */
public record ExtensionRequest(
        @NotNull LocalDateTime newDueAt,
        @Size(max = 255) String note
) {
}