package com.borrowbox.controller;

import com.borrowbox.dto.TrustProfileResponse;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.TrustProfileService;
import com.borrowbox.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrustProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TrustProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrustProfileService trustProfileService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User("Karim", "karim@example.com");
        currentUser.setId(100L);
    }

    private void authenticateAs(User user) {
        when(userService.findByEmail(user.getEmail())).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null,
                        Collections.singletonList(() -> "ROLE_USER")));
    }

    @Test
    void authenticatedGlobalProfileIsReturned() throws Exception {
        authenticateAs(currentUser);
        TrustProfileResponse resp = new TrustProfileResponse(
                null, null, 1, 0, 1, 1, 1, 0, 1.0);
        when(trustProfileService.getTrustProfile(eq(100L), eq(null))).thenReturn(resp);

        mockMvc.perform(get("/api/me/trust-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityId").doesNotExist())
                .andExpect(jsonPath("$.communityName").doesNotExist())
                .andExpect(jsonPath("$.itemsBorrowed").value(1))
                .andExpect(jsonPath("$.itemsLent").value(0))
                .andExpect(jsonPath("$.successfulTransactions").value(1))
                .andExpect(jsonPath("$.onTimeReturnRate").value(1.0));
    }

    @Test
    void authenticatedCommunityScopedProfileIsReturned() throws Exception {
        authenticateAs(currentUser);
        TrustProfileResponse resp = new TrustProfileResponse(
                10L, "Engineering Office", 1, 1, 1, 1, 1, 0, 1.0);
        when(trustProfileService.getTrustProfile(eq(100L), eq(10L))).thenReturn(resp);

        mockMvc.perform(get("/api/me/trust-profile").param("communityId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityId").value(10))
                .andExpect(jsonPath("$.communityName").value("Engineering Office"))
                .andExpect(jsonPath("$.itemsLent").value(1));
    }

    @Test
    void activeMembershipCommunityScopeIsAllowed() throws Exception {
        authenticateAs(currentUser);
        TrustProfileResponse resp = new TrustProfileResponse(
                10L, "Engineering Office", 0, 0, 0, 0, 0, 0, null);
        when(trustProfileService.getTrustProfile(eq(100L), eq(10L))).thenReturn(resp);

        mockMvc.perform(get("/api/me/trust-profile").param("communityId", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void nonMemberCommunityScopeIsForbidden() throws Exception {
        authenticateAs(currentUser);
        when(trustProfileService.getTrustProfile(eq(100L), eq(10L)))
                .thenThrow(new AccessDeniedException("You are not an active member of this community"));

        mockMvc.perform(get("/api/me/trust-profile").param("communityId", "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You are not an active member of this community"));
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost@example.com", null,
                        Collections.singletonList(() -> "ROLE_USER")));
        when(trustProfileService.getTrustProfile(null, null))
                .thenThrow(new UnauthorizedException("Authentication required"));

        mockMvc.perform(get("/api/me/trust-profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void nullRateSerializesAsNullNotZero() throws Exception {
        authenticateAs(currentUser);
        TrustProfileResponse resp = new TrustProfileResponse(
                null, null, 0, 0, 0, 0, 0, 0, null);
        when(trustProfileService.getTrustProfile(eq(100L), eq(null))).thenReturn(resp);

        mockMvc.perform(get("/api/me/trust-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onTimeReturnRate").value(org.hamcrest.Matchers.nullValue()));
    }
}