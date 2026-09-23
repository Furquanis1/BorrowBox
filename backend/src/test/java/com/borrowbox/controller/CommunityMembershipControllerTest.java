package com.borrowbox.controller;

import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.MembershipService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2.4.2 membership review + moderation controller tests. Additions to the
 * existing members endpoints: status/role filters and suspend/reinstate/remove.
 */
@WebMvcTest(CommunityController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CommunityMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityService communityService;

    @MockitoBean
    private MembershipService membershipService;

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

    private MembershipResponse membership(Long id, Long userId, MembershipRole role, MembershipStatus status) {
        return new MembershipResponse(
                id, userId, 900L, "Member", "CSE", role, status,
                null, null, null, null, Map.of());
    }

    @Test
    void membersEndpointRoutesToUnfilteredWhenNoFiltersGiven() throws Exception {
        Mockito.when(communityService.listMembers(eq(900L), eq(currentUser)))
                .thenReturn(List.of(membership(1L, 7L, MembershipRole.MEMBER, MembershipStatus.ACTIVE)));

        mockMvc.perform(get("/api/communities/900/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void membersEndpointWithStatusFilterDelegatesToFiltered() throws Exception {
        Mockito.when(membershipService.listMembers(eq(100L), eq(900L), eq(MembershipStatus.PENDING), eq(null)))
                .thenReturn(List.of(membership(2L, 8L, MembershipRole.MEMBER, MembershipStatus.PENDING)));

        mockMvc.perform(get("/api/communities/900/members")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        Mockito.verify(membershipService)
                .listMembers(eq(100L), eq(900L), eq(MembershipStatus.PENDING), eq(null));
    }

    @Test
    void membersEndpointWithRoleFilterFailsForNonMember() throws Exception {
        Mockito.when(membershipService.listMembers(eq(100L), eq(900L), eq(null), eq(MembershipRole.MANAGER)))
                .thenThrow(new UnauthorizedException(
                        "You must be an active member to view this community's members"));

        mockMvc.perform(get("/api/communities/900/members")
                        .param("role", "MANAGER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendMemberReturnsSuspendedMembership() throws Exception {
        Mockito.when(membershipService.suspend(eq(2L), eq(currentUser)))
                .thenReturn(membership(2L, 8L, MembershipRole.MEMBER, MembershipStatus.SUSPENDED));

        mockMvc.perform(post("/api/communities/900/members/2/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void reinstateMemberReturnsActiveMembership() throws Exception {
        Mockito.when(membershipService.reinstate(eq(2L), eq(currentUser)))
                .thenReturn(membership(2L, 8L, MembershipRole.MEMBER, MembershipStatus.ACTIVE));

        mockMvc.perform(post("/api/communities/900/members/2/reinstate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void removeMemberReturnsLeftMembership() throws Exception {
        Mockito.when(membershipService.removeMember(eq(2L), eq(currentUser)))
                .thenReturn(membership(2L, 8L, MembershipRole.MEMBER, MembershipStatus.LEFT));

        mockMvc.perform(post("/api/communities/900/members/2/remove"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LEFT"));
    }

    @Test
    void suspendMemberByNonManagerIsUnauthorized() throws Exception {
        Mockito.when(membershipService.suspend(eq(2L), eq(currentUser)))
                .thenThrow(new UnauthorizedException("Only an active manager can moderate members"));

        mockMvc.perform(post("/api/communities/900/members/2/suspend"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendPendingMemberIsBadRequest() throws Exception {
        Mockito.when(membershipService.suspend(eq(2L), eq(currentUser)))
                .thenThrow(new BusinessRuleViolationException("Only an active member can be suspended"));

        mockMvc.perform(post("/api/communities/900/members/2/suspend"))
                .andExpect(status().isBadRequest());
    }
}