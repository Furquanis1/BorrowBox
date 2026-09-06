package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.User;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the deterministic seed for Users + Communities + Memberships is
 * idempotent: running the seed initializer twice must not duplicate rows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SeedInitializerTest {

    @Autowired
    private SeedDataInitializer seedDataInitializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetUnitRepository assetUnitRepository;

    @Autowired
    private CommunityListingRepository communityListingRepository;

    @Test
    void seedIsIdempotent() {
        seedDataInitializer.seed();

        long usersAfterFirst = userRepository.count();
        long communitiesAfterFirst = communityRepository.count();
        long membershipsAfterFirst = membershipRepository.count();
        long assetsAfterFirst = assetRepository.count();
        long unitsAfterFirst = assetUnitRepository.count();
        long listingsAfterFirst = communityListingRepository.count();

        assertThat(usersAfterFirst).isGreaterThan(0);
        assertThat(membershipsAfterFirst).isGreaterThan(0);
        assertThat(assetsAfterFirst).isGreaterThan(0);
        assertThat(listingsAfterFirst).isGreaterThan(0);

        seedDataInitializer.seed();

        assertThat(userRepository.count()).isEqualTo(usersAfterFirst);
        assertThat(communityRepository.count()).isEqualTo(communitiesAfterFirst);
        assertThat(membershipRepository.count()).isEqualTo(membershipsAfterFirst);
        assertThat(assetRepository.count()).isEqualTo(assetsAfterFirst);
        assertThat(assetUnitRepository.count()).isEqualTo(unitsAfterFirst);
        assertThat(communityListingRepository.count()).isEqualTo(listingsAfterFirst);
    }

    @Test
    void seedBaselineIsDeterministic() {
        seedDataInitializer.seed();

        assertThat(userRepository.count()).isGreaterThanOrEqualTo(5);
        assertThat(communityRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(assetRepository.count()).isGreaterThanOrEqualTo(5);
        assertThat(assetUnitRepository.count()).isGreaterThanOrEqualTo(6);
        assertThat(communityListingRepository.count()).isGreaterThanOrEqualTo(7);
    }

    @Test
    void everyCreatorHasActiveManagerMembership() {
        seedDataInitializer.seed();

        List<Community> communities = communityRepository.findAll();
        assertThat(communities).isNotEmpty();
        for (Community community : communities) {
            User creator = community.getCreatedBy();
            assertThat(creator).as("creator of %s", community.getName()).isNotNull();
            Membership membership = membershipRepository
                    .findByUserIdAndCommunityId(creator.getId(), community.getId())
                    .orElseThrow(() -> new AssertionError(
                            "Community '" + community.getName() + "' is missing the creator membership"));
            assertThat(membership.getRole()).isEqualTo(MembershipRole.MANAGER);
            assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        }
    }
}
