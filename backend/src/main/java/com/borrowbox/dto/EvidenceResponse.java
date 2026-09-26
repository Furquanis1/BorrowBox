package com.borrowbox.dto;

import com.borrowbox.entity.EvidenceType;

import java.time.LocalDateTime;

/**
 * Transaction-scoped evidence view (V2.2.6). The binary payload is never sent
 * in-list; contentUrl points at the authenticated, participant-only content
 * endpoint. Physical unit identifiers are never exposed.
 *
 * V2.5.1: optional per-evidence condition metadata (note + 1-5 rating) is
 * captured at upload time and returned with the evidence row.
 */
public record EvidenceResponse(
        Long id,
        Long transactionId,
        EvidenceType type,
        Long capturerId,
        String capturerName,
        String contentType,
        Long sizeBytes,
        LocalDateTime capturedAt,
        LocalDateTime createdAt,
        String contentUrl,
        String conditionNote,
        Integer conditionRating
) {
}