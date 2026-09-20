package com.borrowbox.controller;

import com.borrowbox.dto.ReputationEventResponse;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.service.ReputationEventService;
import com.borrowbox.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * V2.3.2 self-scoped reputation ledger surface (ADR-020, ADR-021).
 *
 * <p>Only one read surface exists, and it is inherently self-scoping:
 * {@code GET /api/me/reputation-events} returns <em>the authenticated
 * user's</em> append-only reputation rows, newest first, optionally narrowed
 * to a community the user is (or at the time was) a reputation-bearing
 * participant in.
 *
 * <p>There is no cross-user ledger query, no admin surface, and no
 * reputation-shape or trust-profile endpoint added by this slice — the
 * ledger (ADR-020 "outcomes are durable and derivable at the exact moment a
 * transaction reaches a reputation-bearing terminal state") and the trust
 * profile (V2.3.1) remain separate surfaces.
 */
@RestController
@RequestMapping("/api")
public class ReputationEventController {

    private final ReputationEventService reputationEventService;
    private final UserService userService;

    public ReputationEventController(ReputationEventService reputationEventService,
                                     UserService userService) {
        this.reputationEventService = reputationEventService;
        this.userService = userService;
    }

    @GetMapping("/me/reputation-events")
    public ResponseEntity<List<ReputationEventResponse>> getMyReputationEvents(
            @RequestParam(required = false) Long communityId) {
        User user = currentUser();
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return ResponseEntity.ok(reputationEventService.listForUser(user.getId(), communityId)
                .stream()
                .map(ReputationEventResponse::from)
                .toList());
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
