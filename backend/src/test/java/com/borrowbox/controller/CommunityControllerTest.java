package com.borrowbox.controller;

import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.dto.CommunityResponse;
import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.CommunityAdmissionMode;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.MembershipService;
import com.borrowbox.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommunityController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CommunityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void createCommunityReturnsCreated() throws Exception {
        CommunityCreateRequest req =
                new CommunityCreateRequest("CSE Department", "desc", CommunityType.COLLEGE, null, null, null, null);
        CommunityResponse resp = new CommunityResponse(
                1L, "CSE Department", "desc", CommunityType.COLLEGE,
                com.borrowbox.entity.CommunityStatus.ACTIVE,
                CommunityAdmissionMode.MANAGER_APPROVAL, 1, true);
        Mockito.when(communityService.createCommunity(any(), eq(currentUser))).thenReturn(resp);

        mockMvc.perform(post("/api/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("CSE Department"));
    }

    @Test
    void getMyCommunitiesReturnsList() throws Exception {
        Mockito.when(communityService.listCommunitiesForUser(currentUser))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/communities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getCommunityMembershipRoleTracked() throws Exception {
        CommunityResponse resp = new CommunityResponse(
                1L, "CSE", "desc", CommunityType.COLLEGE,
                com.borrowbox.entity.CommunityStatus.ACTIVE,
                CommunityAdmissionMode.MANAGER_APPROVAL, 1, true);
        Mockito.when(communityService.getCommunityById(eq(1L), eq(currentUser))).thenReturn(resp);

        mockMvc.perform(get("/api/communities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isManager").value(true));
    }

    @Test
    void activeMemberCanReadCommunityMembers() throws Exception {
        MembershipResponse member = new MembershipResponse(
                1L, 100L, 1L, "Ahmed", "CSE",
                MembershipRole.MANAGER, MembershipStatus.ACTIVE, null, null, null, 100L, Map.of());
        Mockito.when(communityService.listMembers(eq(1L), eq(currentUser)))
                .thenReturn(List.of(member));

        mockMvc.perform(get("/api/communities/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(100))
                .andExpect(jsonPath("$[0].role").value("MANAGER"));
    }

    @Test
    void nonMemberCannotReadCommunityMembers() throws Exception {
        Mockito.when(communityService.listMembers(eq(1L), eq(currentUser)))
                .thenThrow(new UnauthorizedException("You must be an active member to view this community's members"));

        mockMvc.perform(get("/api/communities/1/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void joinCommunityReturnsMembership() throws Exception {
        MembershipResponse resp = new MembershipResponse(
                1L, 100L, 1L, "Ahmed", "CSE",
                MembershipRole.MEMBER, MembershipStatus.PENDING,
                com.borrowbox.entity.MembershipVerificationMethod.MANAGER_APPROVAL, null, null, null, Map.of());
        Mockito.when(membershipService.joinCommunity(eq(currentUser), eq(1L), any()))
                .thenReturn(resp);

        mockMvc.perform(post("/api/communities/1/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void leaveCommunityReturnsMembership() throws Exception {
        MembershipResponse resp = new MembershipResponse(
                1L, 100L, 1L, "Ahmed", "CSE",
                MembershipRole.MEMBER, MembershipStatus.LEFT, null, null, null, null, Map.of());
        Mockito.when(membershipService.leave(eq(100L), eq(1L))).thenReturn(resp);

        mockMvc.perform(post("/api/communities/1/leave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LEFT"));
    }

    @Test
    void pendingMembersRequiresManager() throws Exception {
        Mockito.when(membershipService.listPendingForCommunity(eq(100L), eq(1L)))
                .thenThrow(new UnauthorizedException("Only an active manager can view pending memberships"));

        mockMvc.perform(get("/api/communities/1/members/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pendingMembersReturnsList() throws Exception {
        MembershipResponse pending = new MembershipResponse(
                1L, 100L, 1L, "Ahmed", "CSE",
                MembershipRole.MEMBER, MembershipStatus.PENDING,
                com.borrowbox.entity.MembershipVerificationMethod.MANAGER_APPROVAL, null, null, null, Map.of());
        Mockito.when(membershipService.listPendingForCommunity(eq(100L), eq(1L)))
                .thenReturn(List.of(pending));

        mockMvc.perform(get("/api/communities/1/members/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }
}
