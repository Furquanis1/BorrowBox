package com.borrowbox.controller;

import com.borrowbox.dto.HealthResponse;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommunityHealthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CommunityHealthControllerTest {

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

    private HealthResponse health() {
        return new HealthResponse(900L, 5, 1, 1, 4, 3, 75, 1, 4, 1, 25);
    }

    @Test
    void activeManagerSeesHealthCard() throws Exception {
        Mockito.when(dashboardService.getHealth(eq(900L), eq(currentUser))).thenReturn(health());

        mockMvc.perform(get("/api/communities/900/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityId").value(900))
                .andExpect(jsonPath("$.activeMemberCount").value(5))
                .andExpect(jsonPath("$.onTimeReturnRate").value(75));
    }

    @Test
    void nonManagerIsUnauthorized() throws Exception {
        Mockito.when(dashboardService.getHealth(eq(900L), eq(currentUser)))
                .thenThrow(new UnauthorizedException("Only an active manager can view community health"));

        mockMvc.perform(get("/api/communities/900/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveManagerIsUnauthorized() throws Exception {
        Mockito.when(dashboardService.getHealth(eq(900L), eq(currentUser)))
                .thenThrow(new UnauthorizedException("Only an active manager can view community health"));

        mockMvc.perform(get("/api/communities/900/health"))
                .andExpect(status().isUnauthorized());
    }
}