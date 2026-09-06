package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.AssetCreateRequest;
import com.borrowbox.dto.AssetResponse;
import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.dto.ListingCreateRequest;
import com.borrowbox.dto.ListingResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.ListingStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.AssetService;
import com.borrowbox.service.CommunityListingService;
import com.borrowbox.service.CommunityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the CommunityListing service against the real MySQL database,
 * including the locked authorization rules and the canonical seed fixture.
 *
 * Availability is always derived from the shared AssetUnit pool of the asset;
 * listings never carry their own quantity and AssetUnit IDs are never exposed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CommunityListingIntegrationTest {

    @Autowired
    private SeedDataInitializer seedDataInitializer;

    @Autowired
    private CommunityListingService listingService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetUnitRepository assetUnitRepository;

    private User seedUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("missing seed user " + email));
    }

    private Community communityNamed(String name, String createdByEmail) {
        return communityRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .filter(c -> c.getCreatedBy().getEmail().equals(createdByEmail))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed community " + name));
    }

    private Asset seedAssetOf(User owner, String title) {
        return assetRepository.findByOwnerId(owner.getId()).stream()
                .filter(a -> a.getTitle().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed asset " + title));
    }

    private ListingResponse footballListing(Community community, User ahmed) {
        return listingService.listForCommunity(community.getId(), ahmed).stream()
                .filter(r -> r.title().equals("Football"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Football not listed in " + community.getName()));
    }

    private User freshUser(String prefix) {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        User user = new User(prefix, email);
        user.setPasswordHash("test-password");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    @Test
    void canonicalFootballListingsShowIdenticalAggregateAvailability() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        List<Community> communities = List.of(
                communityNamed("CSE Department", "ahmed@example.com"),
                communityNamed("Hostel Block B", "omar@example.com"),
                communityNamed("Engineering Office", "omar@example.com"));

        List<ListingResponse> listings = communities.stream()
                .map(c -> footballListing(c, ahmed))
                .toList();

        assertThat(listings).hasSize(3);
        for (ListingResponse listing : listings) {
            assertThat(listing.assetId()).isEqualTo(
                    seedAssetOf(ahmed, "Football").getId());
            assertThat(listing.listingStatus()).isEqualTo(ListingStatus.LISTED);
        }
        // The football unit pool is shared: one PhysicalAsset, one availability.
        assertThat(listings.get(1).totalUnits()).isEqualTo(listings.get(0).totalUnits());
        assertThat(listings.get(1).availableUnits()).isEqualTo(listings.get(0).availableUnits());
        assertThat(listings.get(1).borrowedUnits()).isEqualTo(listings.get(0).borrowedUnits());
        assertThat(listings.get(2).totalUnits()).isEqualTo(listings.get(0).totalUnits());
        assertThat(listings.get(2).availableUnits()).isEqualTo(listings.get(0).availableUnits());
        assertThat(listings.get(2).borrowedUnits()).isEqualTo(listings.get(0).borrowedUnits());
    }

    @Test
    void unitStatusChangePropagatesToEveryListingOfTheAsset() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        Community cse = communityNamed("CSE Department", "ahmed@example.com");
        Community hostel = communityNamed("Hostel Block B", "omar@example.com");
        Community office = communityNamed("Engineering Office", "omar@example.com");

        ListingResponse before = footballListing(cse, ahmed);

        List<AssetUnit> units = assetUnitRepository.findByAssetId(football.getId());
        boolean flippedBorrowed = false;
        for (AssetUnit unit : units) {
            if (unit.getStatus() == AssetUnitStatus.BORROWED && !flippedBorrowed) {
                unit.setStatus(AssetUnitStatus.AVAILABLE);
                flippedBorrowed = true;
            }
        }
        if (!flippedBorrowed) {
            units.stream().filter(u -> u.getStatus() == AssetUnitStatus.AVAILABLE).findFirst()
                    .ifPresent(u -> u.setStatus(AssetUnitStatus.BORROWED));
        }
        assetUnitRepository.saveAll(units);

        ListingResponse afterCse = footballListing(cse, ahmed);
        ListingResponse afterHostel = footballListing(hostel, ahmed);
        ListingResponse afterOffice = footballListing(office, ahmed);

        assertThat(afterCse.totalUnits()).isEqualTo(before.totalUnits());
        assertThat(afterCse.availableUnits() + afterCse.borrowedUnits()).isEqualTo(afterCse.totalUnits());
        assertThat(afterHostel.availableUnits()).isEqualTo(afterCse.availableUnits());
        assertThat(afterHostel.borrowedUnits()).isEqualTo(afterCse.borrowedUnits());
        assertThat(afterOffice.availableUnits()).isEqualTo(afterCse.availableUnits());
        assertThat(afterOffice.borrowedUnits()).isEqualTo(afterCse.borrowedUnits());
    }

    @Test
    void assetOwnerMustBeActiveMemberToCreateOrViewListings() {
        seedDataInitializer.seed();
        User lister = freshUser("Lister");
        AssetResponse created = assetService.createAsset(
                new AssetCreateRequest("Widget " + UUID.randomUUID(), "desc", null, 2), lister);
        Community cse = communityNamed("CSE Department", "ahmed@example.com");

        assertThatThrownBy(() -> listingService.createOrReactivate(
                created.id(), new ListingCreateRequest(cse.getId()), lister))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> listingService.listForCommunity(cse.getId(), lister))
                .isInstanceOf(UnauthorizedException.class);

        CommunityCreateRequest req = new CommunityCreateRequest(
                "Lister Hub " + UUID.randomUUID(), "desc", CommunityType.COLLEGE, null, null, null, null);
        var communityResponse = communityService.createCommunity(req, lister);

        CommunityListingService.ListingResult result = listingService.createOrReactivate(
                created.id(), new ListingCreateRequest(communityResponse.id()), lister);

        assertThat(result.created()).isTrue();
        ListingResponse response = result.response();
        assertThat(response.communityId()).isEqualTo(communityResponse.id());
        assertThat(response.listingStatus()).isEqualTo(ListingStatus.LISTED);
        assertThat(response.title()).isEqualTo(created.title());
        assertThat(response.totalUnits()).isEqualTo(2L);
        assertThat(response.availableUnits()).isEqualTo(2L);
        assertThat(response.borrowedUnits()).isEqualTo(0L);

        List<ListingResponse> memberView =
                listingService.listForCommunity(communityResponse.id(), lister);
        assertThat(memberView).extracting(ListingResponse::assetId).contains(created.id());
    }

    @Test
    void unlistThenReactivatePreservesListedAtAndHidesFromCommunity() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        Community cse = communityNamed("CSE Department", "ahmed@example.com");

        ListingResponse initial = footballListing(cse, ahmed);
        java.time.LocalDateTime listedAtBefore = initial.listedAt();

        ListingResponse unlisted = listingService.unlist(football.getId(), cse.getId(), ahmed);
        assertThat(unlisted.listingStatus()).isEqualTo(ListingStatus.UNLISTED);
        assertThat(unlisted.listedAt()).isEqualTo(listedAtBefore);

        assertThat(listingService.listForCommunity(cse.getId(), ahmed))
                .extracting(ListingResponse::title)
                .doesNotContain("Football");

        List<ListingResponse> ownerView = listingService.listForAsset(football.getId(), ahmed);
        ListingResponse ownerEntry = ownerView.stream()
                .filter(r -> r.communityId().equals(cse.getId()))
                .findFirst().orElseThrow();
        assertThat(ownerEntry.listingStatus()).isEqualTo(ListingStatus.UNLISTED);
        assertThat(ownerEntry.listedAt()).isEqualTo(listedAtBefore);

        CommunityListingService.ListingResult reactivated = listingService.createOrReactivate(
                football.getId(), new ListingCreateRequest(cse.getId()), ahmed);
        assertThat(reactivated.created()).isFalse();
        assertThat(reactivated.response().listedAt()).isEqualTo(listedAtBefore);

        assertThat(footballListing(cse, ahmed).listingStatus()).isEqualTo(ListingStatus.LISTED);
    }

    @Test
    void duplicateListedListingIsRejectedAgainstRealDatabase() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        Community cse = communityNamed("CSE Department", "ahmed@example.com");

        assertThatThrownBy(() -> listingService.createOrReactivate(
                football.getId(), new ListingCreateRequest(cse.getId()), ahmed))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("This asset is already listed in this community");
    }

    @Test
    void nonOwnerCannotUnlistOrViewAnotherAssetsListings() {
        seedDataInitializer.seed();
        User karim = seedUser("karim@example.com");
        User ahmed = seedUser("ahmed@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        Community cse = communityNamed("CSE Department", "ahmed@example.com");

        assertThatThrownBy(() -> listingService.unlist(football.getId(), cse.getId(), karim))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> listingService.listForAsset(football.getId(), karim))
                .isInstanceOf(UnauthorizedException.class);
    }
}