package com.borrowbox.integration;

import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.dto.FlagCreateRequest;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.MembershipVerificationMethod;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.FlagRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack regression for the V2.4.2 flag read model: FlagController maps
 * FlagResponse.from(...) AFTER FlagService's transaction commits and
 * open-in-view is disabled, so a lazily-loaded assignee used to blow up with a
 * LazyInitializationException whenever a list/patch included an assigned flag.
 * Service methods must initialize the assignee before returning. The test class
 * is intentionally NOT @Transactional so every MockMvc request runs its own
 * short transaction, reproducing the production session boundary.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
public class FlagResponseSerializationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlagRepository flagRepository;

    private User manager;
    private Community community;

    @BeforeEach
    void setUp() {
        manager = new User("FlagSerialManager", "flagserial." + UUID.randomUUID() + "@example.com");
        manager.setPasswordHash("test-password");
        manager.setStatus(UserStatus.ACTIVE);
        manager = userRepository.save(manager);

        community = communityRepository.findById(
                communityService.createCommunity(
                        new CommunityCreateRequest(
                                "FlagSerial " + UUID.randomUUID(), null, CommunityType.CLUB,
                                null, null, null, null),
                        manager).id()
        ).orElseThrow();

        if (!membershipRepository.existsByUserIdAndCommunityId(manager.getId(), community.getId())) {
            Membership membership = new Membership();
            membership.setUser(manager);
            membership.setCommunity(community);
            membership.setRole(MembershipRole.MANAGER);
            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setVerificationMethod(MembershipVerificationMethod.ADMIN);
            membership.setVerifiedBy(manager);
            membership.setVerifiedAt(LocalDateTime.now());
            membershipRepository.save(membership);
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(manager.getEmail(), null,
                        Collections.singletonList(() -> "ROLE_USER")));
    }

    private MvcResult createFlag() throws Exception {
        return mockMvc.perform(post("/api/communities/{communityId}/flags", community.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FlagCreateRequest(FlagType.MANUAL, null, "shelf broken")))
                        .principal(() -> manager.getEmail()))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    void listAndPatchReturnAssignedFlagReadModelWithoutLazyInitializationFailure() throws Exception {
        MvcResult created = createFlag();
        long flagId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/communities/{communityId}/flags/{flagId}", community.getId(), flagId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": " + manager.getId() + "}")
                        .principal(() -> manager.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(manager.getId()))
                .andExpect(jsonPath("$.assigneeName").value(manager.getFullName()));

        mockMvc.perform(get("/api/communities/{communityId}/flags", community.getId())
                        .principal(() -> manager.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assigneeId").value(manager.getId()))
                .andExpect(jsonPath("$[0].assigneeName").value(manager.getFullName()));
    }

    @Test
    void unassignedFlagListSerializesWithoutAssignee() throws Exception {
        createFlag();

        mockMvc.perform(get("/api/communities/{communityId}/flags", community.getId())
                        .principal(() -> manager.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assigneeId").doesNotExist())
                .andExpect(jsonPath("$[0].assigneeName").doesNotExist());
    }
}