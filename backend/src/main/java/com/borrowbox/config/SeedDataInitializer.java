package com.borrowbox.config;

import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityAdmissionMode;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.ListingStatus;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.MembershipVerificationMethod;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V2.1 deterministic, idempotent development seed.
 *
 * Baselines so far:
 *  - V2.1.1: Users + Communities + Memberships.
 *  - V2.1.5: Assets + AssetUnits + CommunityListings (canonical 5/6/7 fixture
 *    including AHMED_FOOTBALL 2/1/1 shared availability).
 *
 * Idempotency keys:
 *   users         -> email
 *   communities   -> (created_by, name)
 *   memberships   -> (user_id, community_id)
 *   assets        -> (owner_id, title); units are reconciled upward only
 *   listings      -> (asset_id, community_id)
 *
 * The seed is strictly additive: it never deletes or rewrites existing rows.
 */
@Component
public class SeedDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final MembershipRepository membershipRepository;
    private final AssetRepository assetRepository;
    private final AssetUnitRepository assetUnitRepository;
    private final CommunityListingRepository communityListingRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDataInitializer(UserRepository userRepository,
                               CommunityRepository communityRepository,
                               MembershipRepository membershipRepository,
                               AssetRepository assetRepository,
                               AssetUnitRepository assetUnitRepository,
                               CommunityListingRepository communityListingRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.assetRepository = assetRepository;
        this.assetUnitRepository = assetUnitRepository;
        this.communityListingRepository = communityListingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Value("${borrowbox.seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        seed();
    }

    @Transactional
    public void seed() {
        User ahmed = user("Ahmed", "ahmed@example.com", "password123");
        User salah = user("Salah", "salah@example.com", "password123");
        User omar = user("Omar", "omar@example.com", "password123");
        User youssef = user("Youssef", "youssef@example.com", "password123");
        User karim = user("Karim", "karim@example.com", "password123");

        Community cse = community(
                "CSE Department", "Computer Science academic community",
                CommunityType.COLLEGE, CommunityAdmissionMode.MANAGER_APPROVAL, ahmed,
                new BigDecimal("29.979200"), new BigDecimal("31.134200"), 2000);
        Community hostel = community(
                "Hostel Block B", "Residential community for Hostel Block B",
                CommunityType.HOSTEL, CommunityAdmissionMode.MANAGER_APPROVAL, omar,
                new BigDecimal("29.985300"), new BigDecimal("31.140000"), 300);
        Community office = community(
                "Engineering Office", "Workplace community for the Engineering Office",
                CommunityType.OFFICE, CommunityAdmissionMode.LOCATION_VERIFIED, omar,
                new BigDecimal("29.987400"), new BigDecimal("31.196800"), 500);

        membership(ahmed, cse, MembershipRole.MANAGER, mapOf("program", "CSE", "year", 4, "section", "A"));
        membership(salah, cse, MembershipRole.MEMBER, mapOf("program", "CSE", "year", 4, "section", "A"));
        membership(omar, hostel, MembershipRole.MANAGER, mapOf("block", "B", "floor", 3, "room", "B-302"));
        membership(youssef, hostel, MembershipRole.MEMBER, mapOf("block", "B", "floor", 3, "room", "B-304"));
        // The canonical "Football listed in CSE + Hostel + Office" and "Drill in
        // CSE" fixtures must satisfy the locked owner-ACTIVE-membership rule, so
        // the fixtures' owners are made ACTIVE members of the extra communities.
        membership(ahmed, hostel, MembershipRole.MEMBER, mapOf("block", "B", "floor", 2, "room", "B-204"));
        membership(ahmed, office, MembershipRole.MEMBER, mapOf("department", "Engineering", "team", "Platform"));
        membership(youssef, cse, MembershipRole.MEMBER, mapOf("program", "CSE", "year", 4, "section", "A"));
        membership(karim, office, MembershipRole.MEMBER, mapOf("department", "Engineering", "team", "QA"));
        membership(omar, office, MembershipRole.MANAGER, mapOf("department", "Engineering", "team", "Leadership"));

        Asset ahmedFootball = asset(ahmed, "Football",
                "Match-size football", List.of(AssetUnitStatus.AVAILABLE, AssetUnitStatus.BORROWED));
        Asset omarCamera = asset(omar, "Camera",
                "Digital camera", List.of(AssetUnitStatus.AVAILABLE));
        Asset youssefDrill = asset(youssef, "Cordless Drill",
                "18V cordless drill", List.of(AssetUnitStatus.AVAILABLE));
        Asset karimCalculator = asset(karim, "Scientific Calculator",
                "CASIO scientific calculator", List.of(AssetUnitStatus.AVAILABLE));
        Asset karimSpareLaptop = asset(karim, "Spare Laptop",
                "Office spare laptop", List.of(AssetUnitStatus.AVAILABLE));

        // Canonical default listings. Spare Laptop intentionally stays unlisted.
        listing(ahmedFootball, cse, ahmed);
        listing(ahmedFootball, hostel, ahmed);
        listing(ahmedFootball, office, ahmed);
        listing(omarCamera, hostel, omar);
        listing(youssefDrill, hostel, youssef);
        listing(youssefDrill, cse, youssef);
        listing(karimCalculator, office, karim);
    }

    private User user(String fullName, String email, String rawPassword) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User(fullName, email);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setStatus(UserStatus.ACTIVE);
            return userRepository.save(user);
        });
    }

    private Community community(String name, String description, CommunityType type,
                                CommunityAdmissionMode admissionMode, User createdBy,
                                BigDecimal latitude, BigDecimal longitude, Integer radiusM) {
        String activeNameKey = name.trim().toLowerCase(java.util.Locale.ROOT);
        return communityRepository
                .findByCreatedByIdAndActiveNameKey(createdBy.getId(), activeNameKey)
                .orElseGet(() -> {
                    Community community = new Community();
                    community.setName(name);
                    community.setDescription(description);
                    community.setType(type);
                    community.setStatus(CommunityStatus.ACTIVE);
                    community.setAdmissionMode(admissionMode);
                    community.setCreatedBy(createdBy);
                    community.setLocationLatitude(latitude);
                    community.setLocationLongitude(longitude);
                    community.setLocationRadiusM(radiusM);
                    community.setActiveNameKey(activeNameKey);
                    return communityRepository.save(community);
                });
    }

    private void membership(User user, Community community, MembershipRole role,
                            Map<String, Object> contextMetadata) {
        if (membershipRepository.existsByUserIdAndCommunityId(user.getId(), community.getId())) {
            return;
        }
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setCommunity(community);
        membership.setRole(role);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setVerificationMethod(MembershipVerificationMethod.ADMIN);
        membership.setVerifiedBy(user);
        membership.setVerifiedAt(LocalDateTime.now());
        membership.setJoinedAt(LocalDateTime.now());
        membership.setContextMetadata(contextMetadata);
        membershipRepository.save(membership);
    }

    /**
     * Seeds an owned asset and its units. Reuses an owner asset with the same
     * title when present; units are reconciled upward only, so first-run unit
     * statuses (e.g. the BORROWED Football fixture) survive restarts and the
     * seed never rewrites or deletes existing user data.
     */
    private Asset asset(User owner, String title, String description,
                        List<AssetUnitStatus> unitStatuses) {
        Asset existing = assetRepository.findByOwnerId(owner.getId()).stream()
                .filter(a -> title.equals(a.getTitle()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            reconcileUnits(existing, unitStatuses.size());
            return existing;
        }
        Asset asset = new Asset();
        asset.setOwner(owner);
        asset.setTitle(title);
        asset.setDescription(description);
        asset.setStatus(AssetStatus.ACTIVE);
        Asset saved = assetRepository.save(asset);
        for (AssetUnitStatus status : unitStatuses) {
            AssetUnit unit = new AssetUnit();
            unit.setAsset(saved);
            unit.setStatus(status);
            assetUnitRepository.save(unit);
        }
        return saved;
    }

    private void reconcileUnits(Asset asset, int desiredCount) {
        long existingCount = assetUnitRepository.findByAssetId(asset.getId()).stream()
                .filter(u -> u.getStatus() != AssetUnitStatus.ARCHIVED)
                .count();
        for (long i = existingCount; i < desiredCount; i++) {
            AssetUnit unit = new AssetUnit();
            unit.setAsset(asset);
            unit.setStatus(AssetUnitStatus.AVAILABLE);
            assetUnitRepository.save(unit);
        }
    }

    /**
     * Seeds a LISTED CommunityListing (asset, community) when absent. The
     * owner must already be an ACTIVE member of the target community; the seed
     * refuses to create a listing that would violate the locked authorization
     * rule, keeping the canonical fixture self-consistent.
     */
    private void listing(Asset asset, Community community, User owner) {
        if (communityListingRepository
                .findByAssetIdAndCommunityId(asset.getId(), community.getId()).isPresent()) {
            return;
        }
        if (membershipRepository.findByUserIdAndCommunityIdAndStatus(
                owner.getId(), community.getId(), MembershipStatus.ACTIVE).isEmpty()) {
            return;
        }
        CommunityListing listing = new CommunityListing();
        listing.setAsset(asset);
        listing.setCommunity(community);
        listing.setListingStatus(ListingStatus.LISTED);
        listing.setListedAt(LocalDateTime.now());
        communityListingRepository.save(listing);
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return map;
    }
}