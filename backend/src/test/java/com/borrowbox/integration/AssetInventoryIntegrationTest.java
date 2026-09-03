package com.borrowbox.integration;

import com.borrowbox.dto.AssetCreateRequest;
import com.borrowbox.dto.AssetResponse;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.AssetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the aggregate inventory model is derived from AssetUnit rows and
 * that the backend is authoritative for availability.
 *
 * Uses REAL MySQL: creates an Asset via the real @Transactional AssetService
 * and mutates AssetUnit statuses directly through the real repository, then
 * re-reads the aggregate counts through the service.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AssetInventoryIntegrationTest {

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetUnitRepository assetUnitRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void aggregateAvailabilityIsDerivedFromAssetUnitStatuses() {
        String suffix = UUID.randomUUID().toString();
        User owner = new User("Inventory", "inventory." + suffix + "@example.com");
        owner.setPasswordHash("test-password");
        owner.setStatus(UserStatus.ACTIVE);
        owner = userRepository.saveAndFlush(owner);

        AssetResponse created = assetService.createAsset(
                new AssetCreateRequest("Football " + suffix, "desc", null, 3), owner);

        assertThat(created.totalUnits()).isEqualTo(3L);
        assertThat(created.availableUnits()).isEqualTo(3L);
        assertThat(created.borrowedUnits()).isEqualTo(0L);

        List<AssetUnit> units = assetUnitRepository.findByAssetId(created.id());
        assertThat(units).hasSize(3);
        units.get(0).setStatus(AssetUnitStatus.BORROWED);
        units.get(1).setStatus(AssetUnitStatus.DAMAGED);
        assetUnitRepository.saveAll(units);

        AssetResponse after = assetService.getAssetById(created.id(), owner);

        assertThat(after.totalUnits()).isEqualTo(3L);
        assertThat(after.availableUnits()).isEqualTo(1L);
        assertThat(after.borrowedUnits()).isEqualTo(1L);
    }
}
