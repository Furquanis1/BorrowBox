package com.borrowbox.controller;

import com.borrowbox.dto.WaitlistEntryResponse;
import com.borrowbox.dto.WaitlistJoinRequest;
import com.borrowbox.entity.User;
import com.borrowbox.service.UserService;
import com.borrowbox.service.WaitlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final UserService userService;

    public WaitlistController(WaitlistService waitlistService, UserService userService) {
        this.waitlistService = waitlistService;
        this.userService = userService;
    }

    @PostMapping("/listings/{listingId}/waitlist")
    public ResponseEntity<WaitlistEntryResponse> join(
            @PathVariable Long listingId,
            @Valid @RequestBody WaitlistJoinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(waitlistService.join(listingId, request, currentUser()));
    }

    @GetMapping("/me/waitlist")
    public ResponseEntity<List<WaitlistEntryResponse>> getMyWaitlist() {
        return ResponseEntity.ok(waitlistService.listForBorrower(currentUser()));
    }

    @DeleteMapping("/me/waitlist/{entryId}")
    public ResponseEntity<WaitlistEntryResponse> leave(@PathVariable Long entryId) {
        return ResponseEntity.ok(waitlistService.leave(entryId, currentUser()));
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