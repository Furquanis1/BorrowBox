package com.borrowbox.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the real security filter chain protects the V2.1.1 endpoints:
 * unauthenticated requests to /api/communities/** and /api/memberships must
 * be rejected before any application code runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityRejectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedAccessToCommunitiesIsRejected() throws Exception {
        mockMvc.perform(get("/api/communities"))
                .andExpect(status().is(403));
    }

    @Test
    void unauthenticatedAccessToCommunityMembersIsRejected() throws Exception {
        mockMvc.perform(get("/api/communities/1/members"))
                .andExpect(status().is(403));
    }

    @Test
    void unauthenticatedAccessToMembershipsIsRejected() throws Exception {
        mockMvc.perform(get("/api/memberships"))
                .andExpect(status().is(403));
    }
}