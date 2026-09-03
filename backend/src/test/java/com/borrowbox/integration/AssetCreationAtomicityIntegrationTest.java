package com.borrowbox.integration;

import com.borrowbox.dto.AssetCreateRequest;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.AssetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Authoritative atomic-creation test for the Asset + N AssetUnit transaction.
 *
 * Uses REAL MySQL, the REAL AssetRepository/AssetUnitRepository and the REAL
 * @Transactional AssetService. The injected AssetUnitRepository is a Mockito
 * spy that wraps the real repository bean, so every save reaches the database.
 * A RuntimeException is deliberately thrown only on the SECOND AssetUnit save,
 * i.e. AFTER at least one unit has already been persisted. We then assert that
 * the Asset AND all of its AssetUnits were rolled back together.
 */
@SpringBootTest
@ActiveProfiles("test")
public class AssetCreationAtomicityIntegrationTest {

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean(name = "assetUnitRepository")
    private AssetUnitRepository assetUnitRepository;

    @Test
    void assetAndAllUnitsRollBackWhenUnitPersistenceFailsAfterFirstUnit() {
        String suffix = UUID.randomUUID().toString();
        User owner = new User("Atomic", "atomic." + suffix + "@example.com");
        owner.setPasswordHash("test-password");
        owner.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(owner);
        User finalOwner = owner;

        AtomicInteger saveCount = new AtomicInteger();
        AtomicReference<Long> generatedAssetId = new AtomicReference<>();
        doAnswer(invocation -> {
            if (saveCount.incrementAndGet() == 2) {
                throw new RuntimeException("simulated 2nd AssetUnit persistence failure");
            }
            AssetUnit unit = invocation.getArgument(0);
            generatedAssetId.set(unit.getAsset().getId());
            return invocation.callRealMethod();
        }).when(assetUnitRepository).save(any(AssetUnit.class));

        AssetCreateRequest request = new AssetCreateRequest(
                "Atomic Asset " + suffix, "desc", null, 2);

        assertThatThrownBy(() -> assetService.createAsset(request, finalOwner))
                .isInstanceOf(RuntimeException.class);

        assertThat(assetRepository.findByOwnerId(finalOwner.getId())).isEmpty();
        assertThat(assetUnitRepository.findByAssetId(generatedAssetId.get())).isEmpty();
    }
}
