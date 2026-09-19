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
import com.borrowbox.entity.MessageKind;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.TransactionMessage;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionMessageRepository;
import com.borrowbox.repository.TransactionRepository;
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
 * V2.2.1 deterministic, idempotent development seed.
 *
 * Baselines so far:
 *  - V2.1.1: Users + Communities + Memberships.
 *  - V2.1.5: Assets + AssetUnits + CommunityListings (canonical 5/6/7 fixture
 *    including AHMED_FOOTBALL 2/1/1 shared availability).
 *  - V2.2.1: Football's non-available unit is backed by an APPROVED transaction
 *    (Salah → Football in CSE); the fixture unit is RESERVED (2/1/0).
 *
 * Idempotency keys:
 *   users         -> email
 *   communities   -> (created_by, name)
 *   memberships   -> (user_id, community_id)
 *   assets        -> (owner_id, title); units are reconciled upward only
 *   listings      -> (asset_id, community_id)
 *   transactions  -> (reserved_unit_id)
 *
 * The seed is strictly additive: it never deletes or rewrites existing rows.
 */
@Component
public class SeedDataInitializer implements ApplicationRunner {

    /**
     * V2.3.1 fixed deterministic base timestamp. Every lifecycle timestamp of
     * the completed seed fixtures (approval, handover, start, return, receipt)
     * is derived from this constant, never from the application clock, so the
     * seed is reproducible and the on-time / late classification is stable.
     */
    private static final LocalDateTime SEED_BASE = LocalDateTime.of(2026, 8, 1, 9, 0, 0);

    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final MembershipRepository membershipRepository;
    private final AssetRepository assetRepository;
    private final AssetUnitRepository assetUnitRepository;
    private final CommunityListingRepository communityListingRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionEventRepository transactionEventRepository;
    private final TransactionMessageRepository transactionMessageRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDataInitializer(UserRepository userRepository,
                               CommunityRepository communityRepository,
                               MembershipRepository membershipRepository,
                               AssetRepository assetRepository,
                               AssetUnitRepository assetUnitRepository,
                               CommunityListingRepository communityListingRepository,
                               TransactionRepository transactionRepository,
                               TransactionEventRepository transactionEventRepository,
                               TransactionMessageRepository transactionMessageRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.assetRepository = assetRepository;
        this.assetUnitRepository = assetUnitRepository;
        this.communityListingRepository = communityListingRepository;
        this.transactionRepository = transactionRepository;
        this.transactionEventRepository = transactionEventRepository;
        this.transactionMessageRepository = transactionMessageRepository;
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

        // V2.2.1: the canonical Football fixture's non-available unit must be
        // backed by a real transaction once the transactions table exists.
        // Salah reserves Ahmed's Football in CSE; the fixture unit flips to
        // RESERVED (the seed never deletes or rewrites existing rows).
        backingFootballTransaction(ahmedFootball, salah, ahmed, cse);

        // V2.3.1: two deterministic completed transactions exercising the full
        // happy-path lifecycle. No asset-unit statuses are changed and no
        // seeded events receive deliveries, so EventPanel stays clean.
        // Fixture A (ON TIME): Karim borrowed Ahmed's Football in the
        // Engineering Office and returned it before the agreed due date.
        completedFixture(ahmedFootball, office, karim, ahmed,
                "Football match practice", 3,
                SEED_BASE.minusDays(11).withHour(8),
                SEED_BASE.minusDays(10).withHour(9),
                SEED_BASE.minusDays(10).withHour(10),
                SEED_BASE.minusDays(10).withHour(10).withMinute(30),
                SEED_BASE.minusDays(8).withHour(11),
                SEED_BASE.minusDays(8).withHour(12),
                SEED_BASE.minusDays(8).withHour(12).withMinute(30));
        // Fixture B (LATE): Omar (a MANAGER) borrowed Youssef's Cordless Drill
        // in Hostel Block B and returned it after the due date. Managers must
        // run through exactly the same trust derivation as ordinary members.
        completedFixture(youssefDrill, hostel, omar, youssef,
                "Wall drilling task", 4,
                SEED_BASE.minusDays(15).withHour(8),
                SEED_BASE.minusDays(14).withHour(9),
                SEED_BASE.minusDays(14).withHour(10),
                SEED_BASE.minusDays(14).withHour(10).withMinute(30),
                SEED_BASE.minusDays(9).withHour(11),
                SEED_BASE.minusDays(9).withHour(12),
                SEED_BASE.minusDays(9).withHour(12).withMinute(30));
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

    /**
     * V2.2.1: the canonical Football fixture's non-available unit must be backed
     * by a real transaction (Salah borrowing Ahmed's Football in CSE, APPROVED).
     * The fixture unit flips BORROWED -> RESERVED and the reservation is held by
     * the transaction. Idempotency key is the reserved-unit relationship: a
     * transaction already holding that unit is left untouched.
     */
    private void backingFootballTransaction(Asset football, User borrower, User lender, Community cse) {
        if (football == null) {
            return;
        }
        List<AssetUnit> units = assetUnitRepository.findByAssetId(football.getId());
        AssetUnit fixtureUnit = units.stream()
                .filter(u -> u.getStatus() == AssetUnitStatus.BORROWED
                        || u.getStatus() == AssetUnitStatus.RESERVED)
                .findFirst()
                .orElse(null);
        if (fixtureUnit == null) {
            return;
        }
        if (transactionRepository.findByReservedUnitId(fixtureUnit.getId()).isPresent()) {
            if (fixtureUnit.getStatus() == AssetUnitStatus.BORROWED) {
                fixtureUnit.setStatus(AssetUnitStatus.RESERVED);
                assetUnitRepository.save(fixtureUnit);
            }
            return;
        }
        CommunityListing listing = communityListingRepository
                .findByAssetIdAndCommunityId(football.getId(), cse.getId())
                .orElse(null);
        if (listing == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        fixtureUnit.setStatus(AssetUnitStatus.RESERVED);
        assetUnitRepository.save(fixtureUnit);

        Transaction txn = new Transaction();
        txn.setCommunity(cse);
        txn.setListing(listing);
        txn.setAsset(football);
        txn.setBorrower(borrower);
        txn.setLender(lender);
        txn.setReservedUnit(fixtureUnit);
        txn.setReservedAt(now);
        txn.setState(TransactionStatus.APPROVED);
        txn.setPurpose("Football match practice");
        txn.setRequestedDurationDays(3);
        txn.setAgreedPurpose("Football match practice");
        txn.setAgreedDurationDays(3);
        txn.setAgreedAt(now);
        txn.setDecidedAt(now);
        txn.setDecidedBy(lender);
        transactionRepository.save(txn);
    }

    /**
     * V2.3.1: seeds one completed transaction and its full happy-path lifecycle
     * (REQUEST_APPROVED, HANDOVER_SCHEDULED, LOAN_STARTED, HANDOVER_CONFIRMED,
     * RETURN_INITIATED, RETURN_REPORTED, LOAN_COMPLETED) plus the exact SYSTEM
     * message rows the production lifecycle emits.
     *
     * Actors match TransactionService exactly: approval/scheduling/loan-start/
     * receipt-of-return are attributed to the lender, receipt-of-item and
     * return initiation/reporting to the borrower.
     *
     * Idempotency key is (asset, borrower, COMPLETED): once present the fixture
     * is never duplicated. A completed transaction holds no reservation, so
     * reservedUnit stays NULL and no asset-unit status is ever touched.
     * Events are created WITHOUT deliveries so /api/me/events stays clean.
     *
     * All lifecycle timestamps derive from the fixed SEED_BASE constant.
     */
    private void completedFixture(Asset asset, Community community,
                                  User borrower, User lender,
                                  String purpose, int agreedDurationDays,
                                  LocalDateTime approvedAt,
                                  LocalDateTime scheduledAt,
                                  LocalDateTime startedAt,
                                  LocalDateTime borrowerConfirmedAt,
                                  LocalDateTime returnInitiatedAt,
                                  LocalDateTime returnReportedAt,
                                  LocalDateTime completedAt) {
        if (transactionRepository.findByAssetIdAndBorrowerIdAndStateIn(
                asset.getId(), borrower.getId(), List.of(TransactionStatus.COMPLETED)).isPresent()) {
            return;
        }
        CommunityListing listing = communityListingRepository
                .findByAssetIdAndCommunityId(asset.getId(), community.getId())
                .orElse(null);
        if (listing == null) {
            return;
        }

        LocalDateTime dueAt = startedAt.plusDays(agreedDurationDays);

        Transaction txn = new Transaction();
        txn.setCommunity(community);
        txn.setListing(listing);
        txn.setAsset(asset);
        txn.setBorrower(borrower);
        txn.setLender(lender);
        txn.setReservedUnit(null);
        txn.setReservedAt(null);
        txn.setState(TransactionStatus.COMPLETED);
        txn.setPurpose(purpose);
        txn.setRequestedDurationDays(agreedDurationDays);
        txn.setAgreedPurpose(purpose);
        txn.setAgreedDurationDays(agreedDurationDays);
        txn.setAgreedAt(approvedAt.minusMinutes(15));
        txn.setDecidedAt(approvedAt);
        txn.setDecidedBy(lender);
        txn.setDecisionNote("Approved");
        txn.setStartedAt(startedAt);
        txn.setDueAt(dueAt);
        txn.setOriginalDueAt(dueAt);
        txn.setBorrowerConfirmedAt(borrowerConfirmedAt);
        txn.setCompletedAt(completedAt);
        Transaction saved = transactionRepository.save(txn);

        seedLifecycleEvent(saved, TransactionEventType.REQUEST_APPROVED, lender);
        seedLifecycleEvent(saved, TransactionEventType.HANDOVER_SCHEDULED, lender);
        seedSystemMessage(saved, "Handover scheduled");
        seedLifecycleEvent(saved, TransactionEventType.LOAN_STARTED, lender);
        seedSystemMessage(saved, "Loan started");
        seedLifecycleEvent(saved, TransactionEventType.HANDOVER_CONFIRMED, borrower);
        seedSystemMessage(saved, "Borrower confirmed receipt");
        seedLifecycleEvent(saved, TransactionEventType.RETURN_INITIATED, borrower);
        seedSystemMessage(saved, "Return initiated");
        seedLifecycleEvent(saved, TransactionEventType.RETURN_REPORTED, borrower);
        seedSystemMessage(saved, "Handback reported");
        seedLifecycleEvent(saved, TransactionEventType.LOAN_COMPLETED, lender);
        seedSystemMessage(saved, "Loan completed");
    }

    private void seedLifecycleEvent(Transaction txn, TransactionEventType eventType, User actor) {
        TransactionEvent event = new TransactionEvent();
        event.setTransaction(txn);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setPayload(null);
        transactionEventRepository.save(event);
    }

    private void seedSystemMessage(Transaction txn, String body) {
        TransactionMessage message = new TransactionMessage();
        message.setTransaction(txn);
        message.setAuthor(null);
        message.setKind(MessageKind.SYSTEM);
        message.setBody(body);
        transactionMessageRepository.save(message);
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return map;
    }
}