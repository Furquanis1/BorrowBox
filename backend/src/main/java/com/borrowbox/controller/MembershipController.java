package com.borrowbox.controller;

import com.borrowbox.dto.AdmissionDecisionRequest;
import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.User;
import com.borrowbox.service.MembershipService;
import com.borrowbox.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {

    private final MembershipService membershipService;
    private final UserService userService;

    public MembershipController(MembershipService membershipService, UserService userService) {
        this.membershipService = membershipService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<MembershipResponse>> getMyMemberships() {
        return ResponseEntity.ok(membershipService.listForUser(userIdFromPrincipal()));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<MembershipResponse> decideMembership(
            @PathVariable Long id,
            @Valid @RequestBody AdmissionDecisionRequest request) {
        return ResponseEntity.ok(membershipService.decide(id, request.decision(), currentUser()));
    }

    private Long userIdFromPrincipal() {
        return currentUser().getId();
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        String email;
        if (principal instanceof UserDetails ud) {
            email = ud.getUsername();
        } else {
            email = principal.toString();
        }
        return userService.findByEmail(email);
    }
}
