package com.borrowbox.dto;

import com.borrowbox.entity.NotificationType;

/**
 * Notification preference view returned to the user.
 */
public record NotificationPreferenceResponse(
        NotificationType type,
        boolean enabled
) {
}
