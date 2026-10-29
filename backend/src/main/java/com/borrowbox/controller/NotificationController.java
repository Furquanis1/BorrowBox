package com.borrowbox.controller;

import com.borrowbox.dto.NotificationResponse;
import com.borrowbox.entity.User;
import com.borrowbox.service.NotificationService;
import com.borrowbox.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService,
                                  UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping("/me/notifications")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications() {
        return ResponseEntity.ok(notificationService.listForUser(currentUser()));
    }

    @GetMapping("/me/notifications/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        return ResponseEntity.ok(notificationService.unreadCount(currentUser()));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id, currentUser()));
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
