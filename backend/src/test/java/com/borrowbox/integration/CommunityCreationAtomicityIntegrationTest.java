package com.borrowbox.integration;

import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies CommunityService.createCommunity is atomic: if persisting the
 * creator Membership fails, the Community insert is rolled back with it.
 *
 * The test profile keeps all production constraints (ddl-auto=validate,
 * version-controlled schema.sql, real MySQL). Only the MembershipRepository
 * is test-doubled to simulate the persistence failure.
 */
@SpringBootTest
@ActiveProfiles("test")
public class CommunityCreationAtomicityIntegrationTest {

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private MembershipRepository membershipRepository;

    @Test
    void communityIsRolledBackWhenCreatorMembershipFails() {
        String email = "atomic." + UUID.randomUUID() + "@example.com";
        User creator = new User("Atomic", email);
        creator.setPasswordHash("test-password");
        creator.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(creator);

        when(membershipRepository.save(any(Membership.class)))
                .thenThrow(new RuntimeException("simulated creator membership persistence failure"));

        CommunityCreateRequest request = new CommunityCreateRequest(
                "Atomic " + UUID.randomUUID(), "desc", CommunityType.COLLEGE, null, null, null, null);

        assertThatThrownBy(() -> communityService.createCommunity(request, creator))
                .isInstanceOf(RuntimeException.class);

        assertThat(communityRepository.findByCreatedById(creator.getId())).isEmpty();
    }
}