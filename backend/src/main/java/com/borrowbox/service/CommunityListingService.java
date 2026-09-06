package com.borrowbox.service;

import com.borrowbox.dto.ListingCreateRequest;
import com.borrowbox.dto.ListingResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Category;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.ListingStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * CommunityListing use cases.
 *
 * Locked invariants (ADR-004 / V2.1 schema section 10):
 *  - An Asset is owned once and may be offered in many communities.
 *  - A listing may be created or activated only when the Asset owner has an
 *    ACTIVE Membership in the target community.
 *  - A listing has no independent quantity: availability is aggregated from
 *    the shared AssetUnit pool exactly like AssetService.
 *  - UNIQUE(asset_id, community_id) is the authoritative duplicate guard.
 */
@Service
public class CommunityListingService {

    private final CommunityListingRepository listingRepository;
    private final AssetRepository assetRepository;
    private final CommunityRepository communityRepository;
    private final AssetUnitRepository assetUnitRepository;
    private final MembershipService membershipService;

    public CommunityListingService(CommunityListingRepository listingRepository,
                                   AssetRepository assetRepository,
                                   CommunityRepository communityRepository,
                                   AssetUnitRepository assetUnitRepository,
                                   MembershipService membershipService) {
        this.listingRepository = listingRepository;
        this.assetRepository = assetRepository;
        this.communityRepository = communityRepository;
        this.assetUnitRepository = assetUnitRepository;
        this.membershipService = membershipService;
    }

    public record ListingResult(ListingResponse response, boolean created) {
    }

    /**
     * Creates a new listing or reactivates an existing UNLISTED one.
     */
    @Transactional
    public ListingResult createOrReactivate(Long assetId, ListingCreateRequest request, User currentUser) {
        requireUser(currentUser);

        Long communityId = request != null ? request.communityId() : null;
        if (communityId == null) {
            throw new BusinessRuleViolationException("A community is required to create a listing");
        }

        Asset asset = requireOwnedActiveAsset(assetId, currentUser);
        requireActiveCommunity(communityId);

        if (!membershipService.isActiveMember(currentUser.getId(), communityId)) {
            throw new UnauthorizedException(
                    "You must be an active member of this community to list an asset in it");
        }

        CommunityListing existing = listingRepository
                .findByAssetIdAndCommunityId(assetId, communityId)
                .orElse(null);

        if (existing != null && existing.getListingStatus() == ListingStatus.LISTED) {
            throw new BusinessRuleViolationException(
                    "This asset is already listed in this community");
        }

        if (existing != null) {
            // UNLISTED -> LISTED reactivation. listedAt is the first listing time
            // and is preserved for the lifetime of the row.
            existing.setListingStatus(ListingStatus.LISTED);
            try {
                return new ListingResult(toResponse(listingRepository.save(existing)), false);
            } catch (DataIntegrityViolationException ex) {
                // Defensive: the DB unique constraint is authoritative.
                throw new BusinessRuleViolationException("This asset is already listed in this community");
            }
        }

        CommunityListing listing = new CommunityListing();
        listing.setAsset(asset);
        listing.setCommunity(communityRepository.getReferenceById(communityId));
        listing.setListingStatus(ListingStatus.LISTED);
        listing.setListedAt(LocalDateTime.now());
        try {
            // saveAndFlush surfaces the UNIQUE(asset_id, community_id) violation
            // synchronously so concurrent duplicate requests translate into a clean 400
            // instead of a deferred rollback-only failure.
            return new ListingResult(toResponse(listingRepository.saveAndFlush(listing)), true);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleViolationException("This asset is already listed in this community");
        }
    }

    /**
     * Owner-only soft unlist: LISTED -> UNLISTED. The row is kept so history is
     * preserved and the same (asset, community) pair can be reactivated later.
     */
    @Transactional
    public ListingResponse unlist(Long assetId, Long communityId, User currentUser) {
        requireUser(currentUser);

        Asset asset = requireOwnedAsset(assetId, currentUser);

        CommunityListing listing = listingRepository
                .findByAssetIdAndCommunityId(assetId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Listing not found for asset " + assetId + " in community " + communityId));

        listing.setListingStatus(ListingStatus.UNLISTED);
        return toResponse(listingRepository.save(listing));
    }

    /**
     * Owner view of every listing for an asset, LISTED and UNLISTED.
     */
    @Transactional(readOnly = true)
    public List<ListingResponse> listForAsset(Long assetId, User currentUser) {
        requireUser(currentUser);

        requireOwnedAsset(assetId, currentUser);

        return listingRepository.findByAssetId(assetId).stream()
                .sorted(Comparator.comparing(CommunityListing::getId))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Member view of the currently LISTED assets in a community. Non-members
     * are rejected; communities are not anonymous public pools.
     */
    @Transactional(readOnly = true)
    public List<ListingResponse> listForCommunity(Long communityId, User currentUser) {
        requireUser(currentUser);

        requireCommunity(communityId);

        if (!membershipService.isActiveMember(currentUser.getId(), communityId)) {
            throw new UnauthorizedException(
                    "Only active members of this community can view its listings");
        }

        return listingRepository.findByCommunityIdAndListingStatus(communityId, ListingStatus.LISTED).stream()
                .sorted(Comparator.comparing(CommunityListing::getId))
                .map(this::toResponse)
                .toList();
    }

    private void requireUser(User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("Authentication is required");
        }
    }

    private Asset requireOwnedAsset(Long assetId, User currentUser) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + assetId));
        if (!asset.getOwner().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("Only the asset owner can manage its listings");
        }
        return asset;
    }

    private Asset requireOwnedActiveAsset(Long assetId, User currentUser) {
        Asset asset = requireOwnedAsset(assetId, currentUser);
        if (asset.getStatus() == AssetStatus.ARCHIVED) {
            throw new BusinessRuleViolationException("An archived asset cannot be listed");
        }
        return asset;
    }

    private void requireCommunity(Long communityId) {
        if (communityRepository.findById(communityId).isEmpty()) {
            throw new ResourceNotFoundException("Community not found with id: " + communityId);
        }
    }

    private void requireActiveCommunity(Long communityId) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found with id: " + communityId));
        if (community.getStatus() == CommunityStatus.ARCHIVED) {
            throw new BusinessRuleViolationException("An archived community cannot accept listings");
        }
    }

    private ListingResponse toResponse(CommunityListing listing) {
        Asset asset = listing.getAsset();
        Community community = listing.getCommunity();
        Long assetId = asset.getId();
        long totalUnits = assetUnitRepository.countByAssetIdAndStatusNot(assetId, AssetUnitStatus.ARCHIVED);
        long availableUnits = assetUnitRepository.countByAssetIdAndStatus(assetId, AssetUnitStatus.AVAILABLE);
        long borrowedUnits = assetUnitRepository.countByAssetIdAndStatus(assetId, AssetUnitStatus.BORROWED);
        Category category = asset.getCategory();
        return new ListingResponse(
                listing.getId(),
                assetId,
                community.getId(),
                community.getName(),
                listing.getListingStatus(),
                listing.getListedAt(),
                asset.getTitle(),
                asset.getDescription(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                totalUnits,
                availableUnits,
                borrowedUnits
        );
    }
}