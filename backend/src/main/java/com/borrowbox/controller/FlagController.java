package com.borrowbox.controller;

import com.borrowbox.dto.FlagCreateRequest;
import com.borrowbox.dto.FlagResponse;
import com.borrowbox.dto.FlagUpdateRequest;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.User;
import com.borrowbox.service.FlagService;
import com.borrowbox.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * V2.4.2 flag management. Every endpoint requires an active community manager;
 * the acting manager is always the reporter on create. All filters are
 * optional and may be combined.
 */
@RestController
@RequestMapping("/api/communities/{communityId}/flags")
public class FlagController {

    private final FlagService flagService;
    private final UserService userService;

    public FlagController(FlagService flagService, UserService userService) {
        this.flagService = flagService;
        this.userService = userService;
    }

    @GetMapping
    public List<FlagResponse> listFlags(@PathVariable Long communityId,
                                        @RequestParam(required = false) FlagStatus status,
                                        @RequestParam(required = false) FlagType flagType,
                                        @RequestParam(required = false) Long transactionId) {
        return flagService.listFiltered(communityId, status, flagType, transactionId, currentUser())
                .stream()
                .map(FlagResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlagResponse createFlag(@PathVariable Long communityId,
                                   @Valid @RequestBody FlagCreateRequest request) {
        User manager = currentUser();
        return FlagResponse.from(flagService.createFlag(
                communityId, request.transactionId(), request.flagType(), request.note(), manager));
    }

    @GetMapping("/{flagId}")
    public FlagResponse getFlag(@PathVariable Long communityId,
                                @PathVariable Long flagId) {
        return FlagResponse.from(flagService.getFlag(communityId, flagId, currentUser()));
    }

    @PatchMapping("/{flagId}")
    public FlagResponse updateFlag(@PathVariable Long communityId,
                                   @PathVariable Long flagId,
                                   @RequestBody FlagUpdateRequest request) {
        return FlagResponse.from(flagService.updateFlag(communityId, flagId, request, currentUser()));
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