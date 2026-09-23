package com.borrowbox.dto;

import java.time.LocalDateTime;

/**
 * V2.4.2 one row of the combined community recent-activity feed. source is
 * either "transaction" or "reputation"; transaction events carry their actor,
 * reputation events carry their attributed user. eventType is the enum name
 * as a string so transaction and reputation types share the feed.
 */
public record RecentActivityDto(
        Long id,
        String source,
        String eventType,
        Long transactionId,
        Long actorId,
        String actorName,
        LocalDateTime occurredAt
) {
}