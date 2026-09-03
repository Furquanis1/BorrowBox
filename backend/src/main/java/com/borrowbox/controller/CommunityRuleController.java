package com.borrowbox.controller;

import com.borrowbox.dto.CommunityRuleRequest;
import com.borrowbox.dto.CommunityRuleResponse;
import com.borrowbox.dto.CommunityRuleUpdateRequest;
import com.borrowbox.entity.User;
import com.borrowbox.service.CommunityRuleService;
import com.borrowbox.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/communities/{id}/rules")
public class CommunityRuleController {

    private final CommunityRuleService communityRuleService;
    private final UserService userService;

    public CommunityRuleController(CommunityRuleService communityRuleService, UserService userService) {
        this.communityRuleService = communityRuleService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<CommunityRuleResponse> createRule(
            @PathVariable Long id,
            @Valid @RequestBody CommunityRuleRequest request) {
        return ResponseEntity.ok(communityRuleService.createRule(id, request, currentUser()));
    }

    @PatchMapping("/{ruleId}")
    public ResponseEntity<CommunityRuleResponse> updateRule(
            @PathVariable Long id,
            @PathVariable Long ruleId,
            @RequestBody CommunityRuleUpdateRequest request) {
        return ResponseEntity.ok(communityRuleService.updateRule(id, ruleId, request, currentUser()));
    }

    @PostMapping("/{ruleId}/activate")
    public ResponseEntity<CommunityRuleResponse> activateRule(
            @PathVariable Long id,
            @PathVariable Long ruleId) {
        return ResponseEntity.ok(communityRuleService.activateRule(id, ruleId, currentUser()));
    }

    @PostMapping("/{ruleId}/deactivate")
    public ResponseEntity<CommunityRuleResponse> deactivateRule(
            @PathVariable Long id,
            @PathVariable Long ruleId) {
        return ResponseEntity.ok(communityRuleService.deactivateRule(id, ruleId, currentUser()));
    }

    @GetMapping
    public ResponseEntity<List<CommunityRuleResponse>> listRules(@PathVariable Long id) {
        return ResponseEntity.ok(communityRuleService.listRulesForCommunity(id, currentUser()));
    }

    @GetMapping("/active")
    public ResponseEntity<List<CommunityRuleResponse>> listActiveRules(@PathVariable Long id) {
        return ResponseEntity.ok(communityRuleService.listActiveRules(id, currentUser()));
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