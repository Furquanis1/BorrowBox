package com.borrowbox.service;

import com.borrowbox.entity.NotificationPreference;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.User;
import com.borrowbox.dto.NotificationPreferenceResponse;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.NotificationPreferenceRepository;
import com.borrowbox.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * V2.6 notification preference service.
 *
 * Users can opt in or out of each notification type. Default is enabled.
 * When a user opts out (enabled = false), the notification service skips
 * creating notifications of that type for that user.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository,
                                         UserRepository userRepository) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns all notification preferences for the given user, ordered by type.
     */
    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(User user) {
        requireUser(user);
        return preferenceRepository.findByUserIdOrderByTypeAsc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Sets (creates or updates) the preference for a given user and type.
     * Returns the updated preference.
     */
    @Transactional
    public NotificationPreferenceResponse setPreference(User user, NotificationType type, boolean enabled) {
        requireUser(user);
        NotificationPreference pref = preferenceRepository.findByUserIdAndType(user.getId(), type)
                .orElseGet(() -> {
                    NotificationPreference p = new NotificationPreference();
                    p.setUser(user);
                    p.setType(type);
                    return p;
                });
        pref.setEnabled(enabled);
        return toResponse(preferenceRepository.save(pref));
    }

    /**
     * Returns true if the user has opted out of the given type (preference exists
     * and enabled = false). Returns false if no preference exists (default
     * enabled) or if enabled = true.
     */
    public boolean isOptedOut(User user, NotificationType type) {
        return preferenceRepository.findByUserIdAndType(user.getId(), type)
                .map(p -> !p.isEnabled())
                .orElse(false);
    }

    private void requireUser(User user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication is required");
        }
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference p) {
        return new NotificationPreferenceResponse(p.getType(), p.isEnabled());
    }
}
