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
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetUnitRepository assetUnitRepository;
    private final CategoryRepository categoryRepository;

    public AssetService(AssetRepository assetRepository,
                        AssetUnitRepository assetUnitRepository,
                        CategoryRepository categoryRepository) {
        this.assetRepository = assetRepository;
        this.assetUnitRepository = assetUnitRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public AssetResponse createAsset(AssetCreateRequest request, User owner) {
        if (owner == null) {
            throw new BusinessRuleViolationException("Authenticated user is required to create an asset");
        }
        if (request.quantity() == null || request.quantity() < 1) {
            throw new BusinessRuleViolationException("Asset quantity must be at least 1");
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category not found with id: " + request.categoryId()));
        }

        Asset asset = new Asset();
        asset.setOwner(owner);
        asset.setTitle(request.title());
        asset.setDescription(request.description());
        asset.setCategory(category);
        asset.setStatus(AssetStatus.ACTIVE);
        Asset savedAsset = assetRepository.save(asset);

        for (int i = 0; i < request.quantity(); i++) {
            AssetUnit unit = new AssetUnit();
            unit.setAsset(savedAsset);
            unit.setStatus(AssetUnitStatus.AVAILABLE);
            assetUnitRepository.save(unit);
        }

        return toResponse(savedAsset);
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> listAssetsForUser(User owner) {
        if (owner == null) {
            throw new BusinessRuleViolationException("Authenticated user is required to list assets");
        }
        List<Asset> assets = new ArrayList<>(assetRepository.findByOwnerId(owner.getId()));
        assets.sort(Comparator
                .comparingInt((Asset a) -> a.getStatus() == AssetStatus.ACTIVE ? 0 : 1)
                .thenComparing(Asset::getId));
        List<AssetResponse> responses = new ArrayList<>();
        for (Asset asset : assets) {
            responses.add(toResponse(asset));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public AssetResponse getAssetById(Long id, User requestor) {
        Asset asset = findOwnedOrThrow(id, requestor);
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse archiveAsset(Long id, User requestor) {
        Asset asset = findOwnedOrThrow(id, requestor);

        List<AssetUnit> units = assetUnitRepository.findByAssetId(id);
        boolean hasBorrowedNonArchived = units.stream().anyMatch(
                u -> u.getStatus() == AssetUnitStatus.BORROWED);
        if (hasBorrowedNonArchived) {
            throw new BusinessRuleViolationException(
                    "Asset cannot be archived because it has a borrowed unit");
        }

        asset.setStatus(AssetStatus.ARCHIVED);
        assetRepository.save(asset);

        for (AssetUnit unit : units) {
            if (unit.getStatus() != AssetUnitStatus.ARCHIVED) {
                unit.setStatus(AssetUnitStatus.ARCHIVED);
                assetUnitRepository.save(unit);
            }
        }

        return toResponse(asset);
    }

    private Asset findOwnedOrThrow(Long id, User requestor) {
        if (requestor == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        return assetRepository.findByIdAndOwnerId(id, requestor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + id));
    }

    private AssetResponse toResponse(Asset asset) {
        Long assetId = asset.getId();
        long totalUnits = assetUnitRepository.countByAssetIdAndStatusNot(assetId, AssetUnitStatus.ARCHIVED);
        long availableUnits = assetUnitRepository.countByAssetIdAndStatus(assetId, AssetUnitStatus.AVAILABLE);
        long borrowedUnits = assetUnitRepository.countByAssetIdAndStatus(assetId, AssetUnitStatus.BORROWED);
        Category category = asset.getCategory();
        return new AssetResponse(
                asset.getId(),
                asset.getTitle(),
                asset.getDescription(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                asset.getStatus(),
                totalUnits,
                availableUnits,
                borrowedUnits
        );
    }
}
