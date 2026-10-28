package com.borrowbox.config;

import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionMessageRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.ReputationEventService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SeedActivationTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CommunityRepository communityRepository = mock(CommunityRepository.class);
    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final AssetUnitRepository assetUnitRepository = mock(AssetUnitRepository.class);
    private final CommunityListingRepository communityListingRepository =
            mock(CommunityListingRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final TransactionEventRepository transactionEventRepository =
            mock(TransactionEventRepository.class);
    private final TransactionMessageRepository transactionMessageRepository =
            mock(TransactionMessageRepository.class);
    private final ReputationEventService reputationEventService = mock(ReputationEventService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void disabledSeedingDoesNotRequireAPasswordOrTouchRepositories() {
        SeedDataInitializer initializer = initializer();
        ReflectionTestUtils.setField(initializer, "seedEnabled", false);
        ReflectionTestUtils.setField(initializer, "seedPassword", null);

        initializer.run(mock(ApplicationArguments.class));

        verifyNoRepositoryInteractions();
    }

    @Test
    void enabledSeedingWithBlankPasswordFailsBeforeTouchingRepositories() {
        SeedDataInitializer initializer = initializer();
        ReflectionTestUtils.setField(initializer, "seedEnabled", true);
        ReflectionTestUtils.setField(initializer, "seedPassword", "   ");

        assertThatThrownBy(() -> initializer.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BORROWBOX_SEED_PASSWORD");

        verifyNoRepositoryInteractions();
    }

    @Test
    void enabledSeedingWithMissingPasswordFailsBeforeTouchingRepositories() {
        SeedDataInitializer initializer = initializer();
        ReflectionTestUtils.setField(initializer, "seedEnabled", true);
        ReflectionTestUtils.setField(initializer, "seedPassword", null);

        assertThatThrownBy(() -> initializer.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BORROWBOX_SEED_PASSWORD");

        verifyNoRepositoryInteractions();
    }

    private SeedDataInitializer initializer() {
        return new SeedDataInitializer(
                userRepository,
                communityRepository,
                membershipRepository,
                assetRepository,
                assetUnitRepository,
                communityListingRepository,
                transactionRepository,
                transactionEventRepository,
                transactionMessageRepository,
                reputationEventService,
                passwordEncoder);
    }

    private void verifyNoRepositoryInteractions() {
        verifyNoInteractions(
                userRepository,
                communityRepository,
                membershipRepository,
                assetRepository,
                assetUnitRepository,
                communityListingRepository,
                transactionRepository,
                transactionEventRepository,
                transactionMessageRepository,
                reputationEventService,
                passwordEncoder);
    }
}
