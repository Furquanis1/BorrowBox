package com.borrowbox.controller;

import com.borrowbox.dto.DashboardResponse;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityDashboardService;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommunityDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CommunityDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityDashboardService dashboardService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User("Ahmed", "ahmed@example.com");
        currentUser.setId(100L);
        Mockito.when(userService.findByEmail("ahmed@example.com")).thenReturn(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ahmed@example.com", null,
                        Collections.singletonList(() -> "ROLE_USER")));
    }

    private DashboardResponse dashboard() {
        return new DashboardResponse(
                900L, 3, 1, 2, 5, 1, 4, 3, 1, 75, 1, 4, 1, 25, 10,
                List.of(), List.of());
    }

    @Test
    void activeManagerSeesDashboardRollUp() throws Exception {
        Mockito.when(dashboardService.getDashboard(eq(900L), eq(currentUser))).thenReturn(dashboard());

        mockMvc.perform(get("/api/communities/900/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityId").value(900))
                .andExpect(jsonPath("$.activeLoanCount").value(3))
                .andExpect(jsonPath("$.overdueLoanCount").value(1))
                .andExpect(jsonPath("$.onTimeReturnRate").value(75))
                .andExpect(jsonPath("$.recentActivity").isArray());
    }

    @Test
    void nonManagerIsUnauthorized() throws Exception {
        Mockito.when(dashboardService.getDashboard(eq(900L), eq(currentUser)))
                .thenThrow(new UnauthorizedException("Only an active manager can view community health"));

        mockMvc.perform(get("/api/communities/900/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveManagerIsUnauthorized() throws Exception {
        Mockito.when(dashboardService.getDashboard(eq(900L), eq(currentUser)))
                .thenThrow(new UnauthorizedException("Only an active manager can view community health"));

        mockMvc.perform(get("/api/communities/900/dashboard"))
                .andExpect(status().isUnauthorized());
    }
}