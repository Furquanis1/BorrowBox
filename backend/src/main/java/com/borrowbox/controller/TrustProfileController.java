package com.borrowbox.controller;

import com.borrowbox.dto.TrustProfileResponse;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.service.TrustProfileService;
import com.borrowbox.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TrustProfileController {

    private final TrustProfileService trustProfileService;
    private final UserService userService;

    public TrustProfileController(TrustProfileService trustProfileService, UserService userService) {
        this.trustProfileService = trustProfileService;
        this.userService = userService;
    }

    @GetMapping("/me/trust-profile")
    public ResponseEntity<TrustProfileResponse> getMyTrustProfile(
            @RequestParam(required = false) Long communityId) {
        User user = currentUser();
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return ResponseEntity.ok(trustProfileService.getTrustProfile(user.getId(), communityId));
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