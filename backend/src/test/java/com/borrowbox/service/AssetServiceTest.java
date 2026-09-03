package com.borrowbox.service;

import com.borrowbox.dto.AssetCreateRequest;
import com.borrowbox.dto.AssetResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Category;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetUnitRepository assetUnitRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private AssetService assetService;

    private User owner;

    @BeforeEach
    void setUp() {
        assetService = new AssetService(assetRepository, assetUnitRepository, categoryRepository);
        owner = new User("Ahmed", "ahmed@example.com");
        owner.setId(100L);
    }

    @Test
    void createAssetWithQuantityCreatesExactlyNAtomicUnits() {
        Asset saved = new Asset();
        saved.setId(500L);
        saved.setOwner(owner);
        saved.setTitle("Football");
        saved.setStatus(AssetStatus.ACTIVE);
        when(assetRepository.save(any(Asset.class))).thenReturn(saved);
        when(assetUnitRepository.save(any(AssetUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetUnitRepository.countByAssetIdAndStatusNot(eq(500L), eq(AssetUnitStatus.ARCHIVED))).thenReturn(3L);
        when(assetUnitRepository.countByAssetIdAndStatus(eq(500L), eq(AssetUnitStatus.AVAILABLE))).thenReturn(3L);
        when(assetUnitRepository.countByAssetIdAndStatus(eq(500L), eq(AssetUnitStatus.BORROWED))).thenReturn(0L);

        AssetResponse response = assetService.createAsset(
                new AssetCreateRequest("Football", "desc", null, 3), owner);

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.status()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(response.totalUnits()).isEqualTo(3L);
        assertThat(response.availableUnits()).isEqualTo(3L);
        assertThat(response.borrowedUnits()).isEqualTo(0L);

        ArgumentCaptor<AssetUnit> unitCaptor = ArgumentCaptor.forClass(AssetUnit.class);
        verify(assetUnitRepository, times(3)).save(unitCaptor.capture());
        assertThat(unitCaptor.getAllValues())
                .hasSize(3)
                .allSatisfy(unit -> {
                    assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.AVAILABLE);
                    assertThat(unit.getAsset().getId()).isEqualTo(500L);
                });
    }

    @Test
    void createAssetWithCategorySetsCategory() {
        Category category = new Category("Sports", "desc");
        category.setId(7L);
        Asset saved = new Asset();
        saved.setId(500L);
        saved.setOwner(owner);
        saved.setTitle("Football");
        saved.setStatus(AssetStatus.ACTIVE);
        saved.setCategory(category);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));
        when(assetRepository.save(any(Asset.class))).thenReturn(saved);
        when(assetUnitRepository.save(any(AssetUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetUnitRepository.countByAssetIdAndStatusNot(eq(500L), eq(AssetUnitStatus.ARCHIVED))).thenReturn(1L);
        when(assetUnitRepository.countByAssetIdAndStatus(eq(500L), eq(AssetUnitStatus.AVAILABLE))).thenReturn(1L);
        when(assetUnitRepository.countByAssetIdAndStatus(eq(500L), eq(AssetUnitStatus.BORROWED))).thenReturn(0L);

        AssetResponse response = assetService.createAsset(
                new AssetCreateRequest("Football", "desc", 7L, 1), owner);

        assertThat(response.categoryId()).isEqualTo(7L);
        assertThat(response.categoryName()).isEqualTo("Sports");
    }

    @Test
    void createAssetWithMissingCategoryThrows() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.createAsset(
                new AssetCreateRequest("Football", "desc", 99L, 1), owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createAssetWithZeroQuantityThrows() {
        assertThatThrownBy(() -> assetService.createAsset(
                new AssetCreateRequest("Football", "desc", null, 0), owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void createAssetWithNullQuantityThrows() {
        assertThatThrownBy(() -> assetService.createAsset(
                new AssetCreateRequest("Football", "desc", null, null), owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void createAssetWithNullOwnerThrows() {
        assertThatThrownBy(() -> assetService.createAsset(
                new AssetCreateRequest("Football", "desc", null, 1), null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void nonOwnerCannotReadOthersAsset() {
        when(assetRepository.findByIdAndOwnerId(500L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.getAssetById(500L, owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ownerCanReadOwnAsset() {
        Asset asset = new Asset();
        asset.setId(500L);
        asset.setOwner(owner);
        asset.setTitle("Football");
        asset.setStatus(AssetStatus.ACTIVE);
        when(assetRepository.findByIdAndOwnerId(500L, 100L)).thenReturn(Optional.of(asset));
        when(assetUnitRepository.countByAssetIdAndStatusNot(eq(500L), eq(AssetUnitStatus.ARCHIVED))).thenReturn(2L);
        when(assetUnitRepository.countByAssetIdAndStatus(eq(500L), eq(AssetUnitStatus.AVAILABLE))).thenReturn(2L);
        when(assetUnitRepository.countByAssetIdAndStatus(eq(500L), eq(AssetUnitStatus.BORROWED))).thenReturn(0L);

        AssetResponse response = assetService.getAssetById(500L, owner);

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.title()).isEqualTo("Football");
        assertThat(response.totalUnits()).isEqualTo(2L);
    }

    @Test
    void archiveRejectedWhenBorrowedUnitExists() {
        Asset asset = new Asset();
        asset.setId(500L);
        asset.setOwner(owner);
        asset.setTitle("Football");
        asset.setStatus(AssetStatus.ACTIVE);
        AssetUnit borrowed = new AssetUnit();
        borrowed.setAsset(asset);
        borrowed.setStatus(AssetUnitStatus.BORROWED);
        when(assetRepository.findByIdAndOwnerId(500L, 100L)).thenReturn(Optional.of(asset));
        when(assetUnitRepository.findByAssetId(500L)).thenReturn(List.of(borrowed));

        assertThatThrownBy(() -> assetService.archiveAsset(500L, owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void archiveSetsAssetAndNonArchivedUnitsToArchivedButPreservesArchivedUnits() {
        Asset asset = new Asset();
        asset.setId(500L);
        asset.setOwner(owner);
        asset.setTitle("Football");
        asset.setStatus(AssetStatus.ACTIVE);
        AssetUnit available = new AssetUnit();
        available.setAsset(asset);
        available.setStatus(AssetUnitStatus.AVAILABLE);
        AssetUnit alreadyArchived = new AssetUnit();
        alreadyArchived.setAsset(asset);
        alreadyArchived.setStatus(AssetUnitStatus.ARCHIVED);
        when(assetRepository.findByIdAndOwnerId(500L, 100L)).thenReturn(Optional.of(asset));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetUnitRepository.findByAssetId(500L))
                .thenReturn(List.of(available, alreadyArchived));
        when(assetUnitRepository.save(any(AssetUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetUnitRepository.countByAssetIdAndStatusNot(eq(500L), eq(AssetUnitStatus.ARCHIVED))).thenReturn(0L);
        when(assetUnitRepository.countByAssetIdAndStatus(eq(500L), eq(AssetUnitStatus.AVAILABLE))).thenReturn(0L);
        when(assetUnitRepository.countByAssetIdAndStatus(eq(500L), eq(AssetUnitStatus.BORROWED))).thenReturn(0L);

        AssetResponse response = assetService.archiveAsset(500L, owner);

        assertThat(response.status()).isEqualTo(AssetStatus.ARCHIVED);
        assertThat(available.getStatus()).isEqualTo(AssetUnitStatus.ARCHIVED);
        assertThat(alreadyArchived.getStatus()).isEqualTo(AssetUnitStatus.ARCHIVED);
    }

    @Test
    void listAssetsOrdersActiveFirstThenIdAscendingAndIncludesArchived() {
        Asset archived = new Asset();
        archived.setId(10L);
        archived.setOwner(owner);
        archived.setTitle("Old");
        archived.setStatus(AssetStatus.ARCHIVED);
        Asset activeLow = new Asset();
        activeLow.setId(5L);
        activeLow.setOwner(owner);
        activeLow.setTitle("B");
        activeLow.setStatus(AssetStatus.ACTIVE);
        Asset activeHigh = new Asset();
        activeHigh.setId(12L);
        activeHigh.setOwner(owner);
        activeHigh.setTitle("A");
        activeHigh.setStatus(AssetStatus.ACTIVE);
        when(assetRepository.findByOwnerId(100L)).thenReturn(List.of(archived, activeLow, activeHigh));
        when(assetUnitRepository.countByAssetIdAndStatusNot(any(Long.class), eq(AssetUnitStatus.ARCHIVED))).thenReturn(1L);
        when(assetUnitRepository.countByAssetIdAndStatus(any(Long.class), eq(AssetUnitStatus.AVAILABLE))).thenReturn(1L);
        when(assetUnitRepository.countByAssetIdAndStatus(any(Long.class), eq(AssetUnitStatus.BORROWED))).thenReturn(0L);

        List<AssetResponse> responses = assetService.listAssetsForUser(owner);

        assertThat(responses).hasSize(3);
        assertThat(responses.get(0).id()).isEqualTo(5L);
        assertThat(responses.get(1).id()).isEqualTo(12L);
        assertThat(responses.get(2).id()).isEqualTo(10L);
        assertThat(responses).extracting(AssetResponse::status)
                .containsExactly(AssetStatus.ACTIVE, AssetStatus.ACTIVE, AssetStatus.ARCHIVED);
    }
}
