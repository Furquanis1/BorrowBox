package com.borrowbox.service;

import com.borrowbox.dto.ListingCreateRequest;
import com.borrowbox.dto.ListingResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.AssetUnitStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CommunityListingServiceTest {

    @Mock
    private CommunityListingRepository listingRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private AssetUnitRepository assetUnitRepository;

    @Mock
    private MembershipService membershipService;

    private CommunityListingService listingService;

    private User owner;
    private Asset football;
    private Community cse;

    @BeforeEach
    void setUp() {
        listingService = new CommunityListingService(
                listingRepository, assetRepository, communityRepository,
                assetUnitRepository, membershipService);

        owner = new User("Ahmed", "ahmed@example.com");
        owner.setId(100L);

        football = new Asset();
        football.setId(500L);
        football.setOwner(owner);
        football.setTitle("Football");
        football.setStatus(AssetStatus.ACTIVE);

        cse = new Community();
        cse.setId(900L);
        cse.setName("CSE Department");
        cse.setStatus(CommunityStatus.ACTIVE);
    }

    private void stubAggregates() {
        when(assetUnitRepository.countByAssetIdAndStatusNot(eq(500L), eq(AssetUnitStatus.ARCHIVED))).thenReturn(2L);
        when(assetUnitRepository.countByAssetIdAndStatus(eq(500L), eq(AssetUnitStatus.AVAILABLE))).thenReturn(1L);
        when(assetUnitRepository.countByAssetIdAndStatus(eq(500L), eq(AssetUnitStatus.BORROWED))).thenReturn(1L);
    }

    private CommunityListing listing(long id) {
        CommunityListing listing = new CommunityListing();
        listing.setId(id);
        listing.setAsset(football);
        listing.setCommunity(cse);
        listing.setListingStatus(ListingStatus.LISTED);
        listing.setListedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        return listing;
    }

    @Test
    void createsNewListingWithAggregateAvailability() {
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));
        when(communityRepository.findById(900L)).thenReturn(Optional.of(cse));
        when(communityRepository.getReferenceById(900L)).thenReturn(cse);
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);
        when(listingRepository.findByAssetIdAndCommunityId(500L, 900L)).thenReturn(Optional.empty());

        CommunityListing persisted = listing(701L);
        when(listingRepository.saveAndFlush(any(CommunityListing.class))).thenReturn(persisted);
        stubAggregates();

        CommunityListingService.ListingResult result = listingService.createOrReactivate(
                500L, new ListingCreateRequest(900L), owner);

        assertThat(result.created()).isTrue();
        ListingResponse response = result.response();
        assertThat(response.assetId()).isEqualTo(500L);
        assertThat(response.communityId()).isEqualTo(900L);
        assertThat(response.communityName()).isEqualTo("CSE Department");
        assertThat(response.listingStatus()).isEqualTo(ListingStatus.LISTED);
        assertThat(response.title()).isEqualTo("Football");
        assertThat(response.totalUnits()).isEqualTo(2L);
        assertThat(response.availableUnits()).isEqualTo(1L);
        assertThat(response.borrowedUnits()).isEqualTo(1L);

        verify(listingRepository).saveAndFlush(any(CommunityListing.class));
    }

    @Test
    void duplicateListedListingIsRejected() {
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));
        when(communityRepository.findById(900L)).thenReturn(Optional.of(cse));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);
        when(listingRepository.findByAssetIdAndCommunityId(500L, 900L))
                .thenReturn(Optional.of(listing(701L)));

        assertThatThrownBy(() -> listingService.createOrReactivate(
                500L, new ListingCreateRequest(900L), owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("This asset is already listed in this community");

        verify(listingRepository, never()).saveAndFlush(any());
    }

    @Test
    void unlistedListingIsReactivatedWithoutChangingListedAt() {
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));
        when(communityRepository.findById(900L)).thenReturn(Optional.of(cse));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);

        CommunityListing unlisted = listing(701L);
        unlisted.setListingStatus(ListingStatus.UNLISTED);
        when(listingRepository.findByAssetIdAndCommunityId(500L, 900L)).thenReturn(Optional.of(unlisted));
        when(listingRepository.save(any(CommunityListing.class))).thenReturn(unlisted);
        stubAggregates();

        CommunityListingService.ListingResult result = listingService.createOrReactivate(
                500L, new ListingCreateRequest(900L), owner);

        assertThat(result.created()).isFalse();
        assertThat(result.response().listingStatus()).isEqualTo(ListingStatus.LISTED);
        assertThat(result.response().listedAt()).isEqualTo(unlisted.getListedAt());
        verify(listingRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateIsSurfacedFromDatabaseConstraint() {
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));
        when(communityRepository.findById(900L)).thenReturn(Optional.of(cse));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);
        when(communityRepository.getReferenceById(900L)).thenReturn(cse);
        when(listingRepository.findByAssetIdAndCommunityId(500L, 900L)).thenReturn(Optional.empty());
        when(listingRepository.saveAndFlush(any(CommunityListing.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '500-900'"));

        assertThatThrownBy(() -> listingService.createOrReactivate(
                500L, new ListingCreateRequest(900L), owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("This asset is already listed in this community");
    }

    @Test
    void nonOwnerCannotCreateListing() {
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(101L);
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));

        assertThatThrownBy(() -> listingService.createOrReactivate(
                500L, new ListingCreateRequest(900L), intruder))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Only the asset owner can manage its listings");
    }

    @Test
    void archivedAssetCannotBeListed() {
        football.setStatus(AssetStatus.ARCHIVED);
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));

        assertThatThrownBy(() -> listingService.createOrReactivate(
                500L, new ListingCreateRequest(900L), owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("An archived asset cannot be listed");
    }

    @Test
    void archivedCommunityCannotAcceptListing() {
        cse.setStatus(CommunityStatus.ARCHIVED);
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));
        when(communityRepository.findById(900L)).thenReturn(Optional.of(cse));

        assertThatThrownBy(() -> listingService.createOrReactivate(
                500L, new ListingCreateRequest(900L), owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("An archived community cannot accept listings");
    }

    @Test
    void nonMemberCannotListInCommunity() {
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));
        when(communityRepository.findById(900L)).thenReturn(Optional.of(cse));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(false);

        assertThatThrownBy(() -> listingService.createOrReactivate(
                500L, new ListingCreateRequest(900L), owner))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("You must be an active member of this community to list an asset in it");
    }

    @Test
    void missingCommunityIdIsRejected() {
        assertThatThrownBy(() -> listingService.createOrReactivate(
                500L, new ListingCreateRequest(null), owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void unauthenticatedUserIsRejected() {
        assertThatThrownBy(() -> listingService.createOrReactivate(500L, new ListingCreateRequest(900L), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void ownerCanSoftUnlist() {
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));
        CommunityListing listed = listing(701L);
        when(listingRepository.findByAssetIdAndCommunityId(500L, 900L)).thenReturn(Optional.of(listed));
        when(listingRepository.save(any(CommunityListing.class))).thenReturn(listed);
        stubAggregates();

        ListingResponse response = listingService.unlist(500L, 900L, owner);

        assertThat(response.listingStatus()).isEqualTo(ListingStatus.UNLISTED);
        assertThat(listed.getListingStatus()).isEqualTo(ListingStatus.UNLISTED);
    }

    @Test
    void unlistMissingListingIsNotFound() {
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));
        when(listingRepository.findByAssetIdAndCommunityId(500L, 900L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.unlist(500L, 900L, owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nonOwnerCannotUnlist() {
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(101L);
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));

        assertThatThrownBy(() -> listingService.unlist(500L, 900L, intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void ownerSeesListedAndUnlistedSortedById() {
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));

        CommunityListing second = listing(702L);
        second.setListingStatus(ListingStatus.UNLISTED);
        stubAggregates();
        when(listingRepository.findByAssetId(500L)).thenReturn(List.of(second, listing(701L)));

        List<ListingResponse> responses = listingService.listForAsset(500L, owner);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(701L);
        assertThat(responses.get(0).listingStatus()).isEqualTo(ListingStatus.LISTED);
        assertThat(responses.get(1).id()).isEqualTo(702L);
        assertThat(responses.get(1).listingStatus()).isEqualTo(ListingStatus.UNLISTED);
    }

    @Test
    void nonOwnerCannotViewAssetListings() {
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(101L);
        when(assetRepository.findById(500L)).thenReturn(Optional.of(football));

        assertThatThrownBy(() -> listingService.listForAsset(500L, intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void activeMemberSeesOnlyListedCommunityListings() {
        when(communityRepository.findById(900L)).thenReturn(Optional.of(cse));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);
        when(listingRepository.findByCommunityIdAndListingStatus(900L, ListingStatus.LISTED))
                .thenReturn(List.of(listing(701L)));
        stubAggregates();

        List<ListingResponse> responses = listingService.listForCommunity(900L, owner);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).listingStatus()).isEqualTo(ListingStatus.LISTED);
        verify(listingRepository).findByCommunityIdAndListingStatus(eq(900L), eq(ListingStatus.LISTED));
    }

    @Test
    void nonMemberCannotViewCommunityListings() {
        when(communityRepository.findById(900L)).thenReturn(Optional.of(cse));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(false);

        assertThatThrownBy(() -> listingService.listForCommunity(900L, owner))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void missingCommunityIsNotFound() {
        when(communityRepository.findById(900L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.listForCommunity(900L, owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}