package com.borrowbox.controller;

import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.dto.CommunityJoinRequest;
import com.borrowbox.dto.CommunityResponse;
import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.MembershipService;
import com.borrowbox.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/communities")
public class CommunityController {

    private final CommunityService communityService;
    private final MembershipService membershipService;
    private final UserService userService;

    public CommunityController(CommunityService communityService,
                               MembershipService membershipService,
                               UserService userService) {
        this.communityService = communityService;
        this.membershipService = membershipService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<CommunityResponse>> getMyCommunities() {
        return ResponseEntity.ok(communityService.listCommunitiesForUser(currentUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommunityResponse> getCommunity(@PathVariable Long id) {
        return ResponseEntity.ok(communityService.getCommunityById(id, currentUser()));
    }

    @PostMapping
    public ResponseEntity<CommunityResponse> createCommunity(
            @Valid @RequestBody CommunityCreateRequest request) {
        CommunityResponse created = communityService.createCommunity(request, currentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<MembershipResponse>> getMembers(
            @PathVariable Long id,
            @RequestParam(required = false) MembershipStatus status,
            @RequestParam(required = false) MembershipRole role) {
        if (status == null && role == null) {
            return ResponseEntity.ok(communityService.listMembers(id, currentUser()));
        }
        return ResponseEntity.ok(
                membershipService.listMembers(currentUser().getId(), id, status, role));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<MembershipResponse> joinCommunity(
            @PathVariable Long id,
            @RequestBody(required = false) CommunityJoinRequest request) {
        CommunityJoinRequest body = request != null ? request : new CommunityJoinRequest(null, null, null);
        return ResponseEntity.ok(membershipService.joinCommunity(currentUser(), id, body));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<MembershipResponse> leaveCommunity(@PathVariable Long id) {
        return ResponseEntity.ok(membershipService.leave(currentUser().getId(), id));
    }

    @GetMapping("/{id}/members/pending")
    public ResponseEntity<List<MembershipResponse>> getPendingMembers(@PathVariable Long id) {
        return ResponseEntity.ok(membershipService.listPendingForCommunity(currentUser().getId(), id));
    }

    @PostMapping("/{id}/members/{membershipId}/suspend")
    public ResponseEntity<MembershipResponse> suspendMember(@PathVariable Long id,
                                                            @PathVariable Long membershipId) {
        return ResponseEntity.ok(membershipService.suspend(membershipId, currentUser()));
    }

    @PostMapping("/{id}/members/{membershipId}/reinstate")
    public ResponseEntity<MembershipResponse> reinstateMember(@PathVariable Long id,
                                                              @PathVariable Long membershipId) {
        return ResponseEntity.ok(membershipService.reinstate(membershipId, currentUser()));
    }

    @PostMapping("/{id}/members/{membershipId}/remove")
    public ResponseEntity<MembershipResponse> removeMember(@PathVariable Long id,
                                                           @PathVariable Long membershipId) {
        return ResponseEntity.ok(membershipService.removeMember(membershipId, currentUser()));
    }

    private com.borrowbox.entity.User currentUser() {
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
