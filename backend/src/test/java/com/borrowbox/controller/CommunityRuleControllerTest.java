package com.borrowbox.controller;

import com.borrowbox.dto.CommunityRuleRequest;
import com.borrowbox.dto.CommunityRuleResponse;
import com.borrowbox.dto.CommunityRuleUpdateRequest;
import com.borrowbox.entity.CommunityRuleType;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityRuleService;
import com.borrowbox.service.JwtService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommunityRuleController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CommunityRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommunityRuleService communityRuleService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User currentUser;

    private CommunityRuleResponse ruleResponse(Long id, CommunityRuleType type, CommunityStatus status) {
        return new CommunityRuleResponse(
                id, 1L, type, Map.of("text", "Be kind"),
                status, 100L, 100L, null, null);
    }

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
    void createRuleReturnsRule() throws Exception {
        CommunityRuleRequest req = new CommunityRuleRequest(
                CommunityRuleType.ADMISSION_NOTE, Map.of("text", "Be kind"));
        CommunityRuleResponse resp = ruleResponse(1L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        Mockito.when(communityRuleService.createRule(eq(1L), any(), eq(currentUser))).thenReturn(resp);

        mockMvc.perform(post("/api/communities/1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleType").value("ADMISSION_NOTE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updateRuleReturnsRule() throws Exception {
        CommunityRuleUpdateRequest req = new CommunityRuleUpdateRequest(Map.of("text", "Kind"), true);
        CommunityRuleResponse resp = ruleResponse(5L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        Mockito.when(communityRuleService.updateRule(eq(1L), eq(5L), any(), eq(currentUser))).thenReturn(resp);

        mockMvc.perform(patch("/api/communities/1/rules/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void activateRuleReturnsRule() throws Exception {
        CommunityRuleResponse resp = ruleResponse(5L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        Mockito.when(communityRuleService.activateRule(eq(1L), eq(5L), eq(currentUser))).thenReturn(resp);

        mockMvc.perform(post("/api/communities/1/rules/5/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deactivateRuleReturnsRule() throws Exception {
        CommunityRuleResponse resp = ruleResponse(5L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ARCHIVED);
        Mockito.when(communityRuleService.deactivateRule(eq(1L), eq(5L), eq(currentUser))).thenReturn(resp);

        mockMvc.perform(post("/api/communities/1/rules/5/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void managerCanListAllRules() throws Exception {
        CommunityRuleResponse resp = ruleResponse(1L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        Mockito.when(communityRuleService.listRulesForCommunity(eq(1L), eq(currentUser)))
                .thenReturn(List.of(resp));

        mockMvc.perform(get("/api/communities/1/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleType").value("ADMISSION_NOTE"));
    }

    @Test
    void activeMemberCanListActiveRules() throws Exception {
        CommunityRuleResponse resp = ruleResponse(1L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        Mockito.when(communityRuleService.listActiveRules(eq(1L), eq(currentUser)))
                .thenReturn(List.of(resp));

        mockMvc.perform(get("/api/communities/1/rules/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void managerMutationRejectedByNonManager() throws Exception {
        Mockito.when(communityRuleService.createRule(eq(1L), any(), eq(currentUser)))
                .thenThrow(new UnauthorizedException("Only an active manager can manage community rules"));

        mockMvc.perform(post("/api/communities/1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeRulesReadRejectedByNonMember() throws Exception {
        Mockito.when(communityRuleService.listActiveRules(eq(1L), eq(currentUser)))
                .thenThrow(new UnauthorizedException(
                        "You must be an active member to view this community's rules"));

        mockMvc.perform(get("/api/communities/1/rules/active"))
                .andExpect(status().isUnauthorized());
    }
}