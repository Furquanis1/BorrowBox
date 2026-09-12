package com.borrowbox.dto;

import com.borrowbox.entity.MessageKind;

import java.time.LocalDateTime;

/**
 * Conversation message view. Author ids/names are exposed only for USER
 * messages; SYSTEM messages carry a null author. Timestamps are always
 * server-generated. No asset/unit identifiers are ever included.
 */
public record TransactionMessageResponse(
        Long id,
        Long transactionId,
        Long authorId,
        String authorName,
        MessageKind kind,
        String body,
        LocalDateTime createdAt
) {
}