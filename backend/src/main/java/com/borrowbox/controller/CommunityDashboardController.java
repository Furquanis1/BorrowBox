package com.borrowbox.controller;

import com.borrowbox.dto.DashboardResponse;
import com.borrowbox.entity.User;
import com.borrowbox.service.CommunityDashboardService;
import com.borrowbox.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2.4.2 manager dashboard roll-up for one community.
 */
@RestController
@RequestMapping("/api/communities/{communityId}/dashboard")
public class CommunityDashboardController {

    private final CommunityDashboardService dashboardService;
    private final UserService userService;

    public CommunityDashboardController(CommunityDashboardService dashboardService,
                                        UserService userService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
    }

    @GetMapping
    public DashboardResponse getDashboard(@PathVariable Long communityId) {
        return dashboardService.getDashboard(communityId, currentUser());
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