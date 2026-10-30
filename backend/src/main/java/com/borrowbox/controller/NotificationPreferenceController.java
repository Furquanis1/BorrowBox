package com.borrowbox.controller;

import com.borrowbox.dto.NotificationPreferenceResponse;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.User;
import com.borrowbox.service.NotificationPreferenceService;
import com.borrowbox.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;
    private final UserService userService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService,
                                            UserService userService) {
        this.preferenceService = preferenceService;
        this.userService = userService;
    }

    @GetMapping("/me/notification-preferences")
    public ResponseEntity<List<NotificationPreferenceResponse>> getMyPreferences() {
        return ResponseEntity.ok(preferenceService.getPreferences(currentUser()));
    }

    @PatchMapping("/me/notification-preferences/{type}")
    public ResponseEntity<NotificationPreferenceResponse> setPreference(
            @PathVariable NotificationType type,
            @RequestBody boolean enabled) {
        return ResponseEntity.ok(preferenceService.setPreference(currentUser(), type, enabled));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) {
            email = ud.getUsername();
        } else {
            email = principal.toString();
        }
        return userService.findByEmail(email);
    }
}
