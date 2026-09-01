package com.borrowbox.controller;

import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.User;
import com.borrowbox.repository.UserRepository;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MembershipController.class)
@AutoConfigureMockMvc(addFilters = false)
public class MembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void getMyMembershipsReturnsReadOnlyList() throws Exception {
        MembershipResponse resp = new MembershipResponse(
                1L, 100L, 500L, "Ahmed", "CSE Department",
                MembershipRole.MANAGER, MembershipStatus.ACTIVE,
                null, null, null, Map.of("program", "CSE", "year", 4, "section", "A"));
        Mockito.when(membershipService.listForUser(eq(100L))).thenReturn(Collections.singletonList(resp));

        mockMvc.perform(get("/api/memberships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(100))
                .andExpect(jsonPath("$[0].communityId").value(500))
                .andExpect(jsonPath("$[0].role").value("MANAGER"))
                .andExpect(jsonPath("$[0].contextMetadata.program").value("CSE"));
    }
}
