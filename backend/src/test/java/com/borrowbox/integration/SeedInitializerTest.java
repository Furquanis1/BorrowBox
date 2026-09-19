package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.entity.*;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.TransactionEventDeliveryRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionMessageRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the deterministic V2.1 seed is idempotent and produces the
 * canonical fixture values documented in BORROWBOX_V2_1_SEED_DATA_AND_INITIALIZATION.ipynb.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SeedInitializerTest {

    @Autowired private SeedDataInitializer seedDataInitializer;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetUnitRepository assetUnitRepository;
    @Autowired private CommunityListingRepository communityListingRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionEventRepository transactionEventRepository;
    @Autowired private TransactionEventDeliveryRepository transactionEventDeliveryRepository;
    @Autowired private TransactionMessageRepository transactionMessageRepository;

    // ── Idempotency ──────────────────────────────────────────────────

    @Test
    void seedIsIdempotent() {
        seedDataInitializer.seed();

        long usersAfterFirst       = userRepository.count();
        long communitiesAfterFirst = communityRepository.count();
        long membershipsAfterFirst = membershipRepository.count();
        long assetsAfterFirst      = assetRepository.count();
        long unitsAfterFirst       = assetUnitRepository.count();
        long listingsAfterFirst    = communityListingRepository.count();
        long transactionsAfterFirst = transactionRepository.count();

        assertThat(usersAfterFirst).isGreaterThan(0);
        assertThat(membershipsAfterFirst).isGreaterThan(0);
        assertThat(assetsAfterFirst).isGreaterThan(0);
        assertThat(listingsAfterFirst).isGreaterThan(0);

        seedDataInitializer.seed();

        assertThat(userRepository.count()).isEqualTo(usersAfterFirst);
        assertThat(communityRepository.count()).isEqualTo(communitiesAfterFirst);
        assertThat(membershipRepository.count()).isEqualTo(membershipsAfterFirst);
        assertThat(assetRepository.count()).isEqualTo(assetsAfterFirst);
        assertThat(assetUnitRepository.count()).isEqualTo(unitsAfterFirst);
        assertThat(communityListingRepository.count()).isEqualTo(listingsAfterFirst);
        assertThat(transactionRepository.count()).isEqualTo(transactionsAfterFirst);
    }

    // ── Row-count baseline ────────────────────────────────────────────

    @Test
    void seedBaselineIsDeterministic() {
        seedDataInitializer.seed();

        assertThat(userRepository.count()).isGreaterThanOrEqualTo(5);
        assertThat(communityRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(assetRepository.count()).isGreaterThanOrEqualTo(5);
        assertThat(assetUnitRepository.count()).isGreaterThanOrEqualTo(6);
        assertThat(communityListingRepository.count()).isGreaterThanOrEqualTo(7);
    }

    // ── Creator-manager invariant ──────────────────────────────────────

    @Test
    void everyCreatorHasActiveManagerMembership() {
        seedDataInitializer.seed();

        List<Community> communities = communityRepository.findAll();
        assertThat(communities).isNotEmpty();
        for (Community community : communities) {
            User creator = community.getCreatedBy();
            assertThat(creator).as("creator of %s", community.getName()).isNotNull();
            Membership membership = membershipRepository
                    .findByUserIdAndCommunityId(creator.getId(), community.getId())
                    .orElseThrow(() -> new AssertionError(
                            "Community '" + community.getName() + "' is missing the creator membership"));
            assertThat(membership.getRole()).isEqualTo(MembershipRole.MANAGER);
            assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        }
    }

    // ── Canonical user emails ──────────────────────────────────────────

    @Test
    void allCanonicalUsersExist() {
        seedDataInitializer.seed();

        for (String email : List.of(
                "ahmed@example.com", "salah@example.com", "omar@example.com",
                "youssef@example.com", "karim@example.com")) {
            assertThat(userRepository.findByEmail(email))
                    .as("user %s", email)
                    .isPresent();
        }
    }

    // ── Canonical community names and types ────────────────────────────

    @Test
    void canonicalCommunitiesExist() {
        seedDataInitializer.seed();

        Map<String, Community> byName = communityRepository.findAll().stream()
                .collect(Collectors.toMap(Community::getName, c -> c));

        assertThat(byName).containsKey("CSE Department");
        assertThat(byName.get("CSE Department").getType()).isEqualTo(CommunityType.COLLEGE);

        assertThat(byName).containsKey("Hostel Block B");
        assertThat(byName.get("Hostel Block B").getType()).isEqualTo(CommunityType.HOSTEL);

        assertThat(byName).containsKey("Engineering Office");
        assertThat(byName.get("Engineering Office").getType()).isEqualTo(CommunityType.OFFICE);
    }

    // ── Canonical asset titles and unit counts ─────────────────────────

    @Test
    void canonicalAssetsHaveCorrectUnits() {
        seedDataInitializer.seed();

        User ahmed = userRepository.findByEmail("ahmed@example.com").orElseThrow();
        User omar  = userRepository.findByEmail("omar@example.com").orElseThrow();
        User youssef = userRepository.findByEmail("youssef@example.com").orElseThrow();
        User karim = userRepository.findByEmail("karim@example.com").orElseThrow();

        Asset football = findAssetByOwnerAndTitle(ahmed, "Football");
        List<AssetUnit> footballUnits = assetUnitRepository.findByAssetId(football.getId());
        assertThat(footballUnits).hasSize(2);
        assertThat(footballUnits).extracting(AssetUnit::getStatus)
                .containsExactlyInAnyOrder(AssetUnitStatus.AVAILABLE, AssetUnitStatus.RESERVED);

        assertThat(findAssetByOwnerAndTitle(omar, "Camera")).isNotNull();
        assertThat(findAssetByOwnerAndTitle(youssef, "Cordless Drill")).isNotNull();
        assertThat(findAssetByOwnerAndTitle(karim, "Scientific Calculator")).isNotNull();
        assertThat(findAssetByOwnerAndTitle(karim, "Spare Laptop")).isNotNull();
    }

    // ── Canonical listing counts ───────────────────────────────────────

    @Test
    void canonicalListingCounts() {
        seedDataInitializer.seed();

        User ahmed  = userRepository.findByEmail("ahmed@example.com").orElseThrow();
        User omar   = userRepository.findByEmail("omar@example.com").orElseThrow();
        User youssef = userRepository.findByEmail("youssef@example.com").orElseThrow();
        User karim  = userRepository.findByEmail("karim@example.com").orElseThrow();

        Asset football    = findAssetByOwnerAndTitle(ahmed, "Football");
        Asset camera      = findAssetByOwnerAndTitle(omar, "Camera");
        Asset drill       = findAssetByOwnerAndTitle(youssef, "Cordless Drill");
        Asset calculator  = findAssetByOwnerAndTitle(karim, "Scientific Calculator");
        Asset spareLaptop = findAssetByOwnerAndTitle(karim, "Spare Laptop");

        // Football must be listed in at least the 3 canonical communities
        // (CSE, Hostel, Office). Extra listings may exist from prior test runs.
        List<CommunityListing> footballListings = communityListingRepository.findByAssetId(football.getId());
        assertThat(footballListings).hasSizeGreaterThanOrEqualTo(3);
        List<String> footballCommunities = footballListings.stream()
                .map(l -> l.getCommunity().getName()).toList();
        assertThat(footballCommunities).contains("CSE Department", "Hostel Block B", "Engineering Office");

        assertThat(communityListingRepository.findByAssetId(camera.getId())).hasSizeGreaterThanOrEqualTo(1);
        assertThat(communityListingRepository.findByAssetId(drill.getId())).hasSizeGreaterThanOrEqualTo(2);
        assertThat(communityListingRepository.findByAssetId(calculator.getId())).hasSizeGreaterThanOrEqualTo(1);
        assertThat(communityListingRepository.findByAssetId(spareLaptop.getId())).isEmpty();
    }

    // ── Spare Laptop is unlisted ───────────────────────────────────────

    @Test
    void spareLaptopHasNoListings() {
        seedDataInitializer.seed();

        User karim = userRepository.findByEmail("karim@example.com").orElseThrow();
        Asset spareLaptop = findAssetByOwnerAndTitle(karim, "Spare Laptop");
        assertThat(communityListingRepository.findByAssetId(spareLaptop.getId())).isEmpty();
    }

    // ── Football shared availability across all listings ───────────────

    @Test
    void footballAvailabilityConsistentAcrossListings() {
        seedDataInitializer.seed();

        User ahmed = userRepository.findByEmail("ahmed@example.com").orElseThrow();
        Asset football = findAssetByOwnerAndTitle(ahmed, "Football");

        List<AssetUnit> units = assetUnitRepository.findByAssetId(football.getId());
        long total     = units.size();
        long available = units.stream().filter(u -> u.getStatus() == AssetUnitStatus.AVAILABLE).count();
        long borrowed  = units.stream().filter(u -> u.getStatus() == AssetUnitStatus.BORROWED).count();
        long reserved  = units.stream().filter(u -> u.getStatus() == AssetUnitStatus.RESERVED).count();

        assertThat(total).isEqualTo(2);
        assertThat(available).isEqualTo(1);
        assertThat(borrowed).isEqualTo(0);
        assertThat(reserved).isEqualTo(1);

        List<CommunityListing> listings = communityListingRepository.findByAssetId(football.getId());
        assertThat(listings).hasSizeGreaterThanOrEqualTo(3);
    }

    // ── Football fixture is transaction-backed (V2.2.1) ───────────────

    @Test
    void footballFixtureIsTransactionBacked() {
        seedDataInitializer.seed();

        User ahmed = userRepository.findByEmail("ahmed@example.com").orElseThrow();
        User salah = userRepository.findByEmail("salah@example.com").orElseThrow();
        Asset football = findAssetByOwnerAndTitle(ahmed, "Football");

        AssetUnit reservedUnit = assetUnitRepository.findByAssetId(football.getId()).stream()
                .filter(u -> u.getStatus() == AssetUnitStatus.RESERVED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a RESERVED Football unit"));

        Transaction backing = transactionRepository.findByReservedUnitId(reservedUnit.getId())
                .orElseThrow(() -> new AssertionError("RESERVED Football unit has no backing transaction"));

        assertThat(backing.getState()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(backing.getBorrower().getEmail()).isEqualTo("salah@example.com");
        assertThat(backing.getLender().getEmail()).isEqualTo("ahmed@example.com");
        assertThat(backing.getCommunity().getName()).isEqualTo("CSE Department");
        assertThat(backing.getPurpose()).isEqualTo("Football match practice");
        assertThat(backing.getRequestedDurationDays()).isEqualTo(3);
        assertThat(backing.getAgreedPurpose()).isEqualTo("Football match practice");
        assertThat(backing.getAgreedDurationDays()).isEqualTo(3);
        assertThat(backing.getReservedUnit()).isSameAs(reservedUnit);
    }

    // ── Admission mode fixtures ────────────────────────────────────────

    @Test
    void canonicalAdmissionModes() {
        seedDataInitializer.seed();

        Map<String, Community> byName = communityRepository.findAll().stream()
                .collect(Collectors.toMap(Community::getName, c -> c));

        assertThat(byName.get("CSE Department").getAdmissionMode())
                .isEqualTo(CommunityAdmissionMode.MANAGER_APPROVAL);
        assertThat(byName.get("Hostel Block B").getAdmissionMode())
                .isEqualTo(CommunityAdmissionMode.MANAGER_APPROVAL);
        assertThat(byName.get("Engineering Office").getAdmissionMode())
                .isEqualTo(CommunityAdmissionMode.LOCATION_VERIFIED);
    }

    // ── V2.3.1 completed trust-ledger fixtures ──────────────────────────

    /**
     * Resolves a seeded completed fixture by its natural key (asset, borrower).
     * The shared MySQL test database may hold unrelated committed rows, so all
     * V2.3.1 assertions are scoped to the two fixtures the seed owns.
     */
    private Transaction seededCompletedFixture(String ownerEmail, String assetTitle, String borrowerEmail) {
        User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
        User borrower = userRepository.findByEmail(borrowerEmail).orElseThrow();
        Asset asset = findAssetByOwnerAndTitle(owner, assetTitle);
        return transactionRepository.findByAssetIdAndBorrowerIdAndStateIn(
                        asset.getId(), borrower.getId(), List.of(TransactionStatus.COMPLETED))
                .orElseThrow(() -> new AssertionError(
                        "no completed fixture for " + assetTitle + " / " + borrowerEmail));
    }

    @Test
    void v231SeedsExactlyOneCompletedFixturePerPair() {
        seedDataInitializer.seed();

        User karim = userRepository.findByEmail("karim@example.com").orElseThrow();
        User omar = userRepository.findByEmail("omar@example.com").orElseThrow();
        Asset football = findAssetByOwnerAndTitle(
                userRepository.findByEmail("ahmed@example.com").orElseThrow(), "Football");
        Asset drill = findAssetByOwnerAndTitle(
                userRepository.findByEmail("youssef@example.com").orElseThrow(), "Cordless Drill");

        assertThat(transactionRepository.findByAssetIdAndBorrowerIdAndStateIn(
                football.getId(), karim.getId(), List.of(TransactionStatus.COMPLETED)))
                .isPresent();
        assertThat(transactionRepository.findByAssetIdAndBorrowerIdAndStateIn(
                drill.getId(), omar.getId(), List.of(TransactionStatus.COMPLETED)))
                .isPresent();
    }

    @Test
    void v231CompletedFixturesMatchExpectedPartiesAndTiming() {
        seedDataInitializer.seed();

        Transaction football = seededCompletedFixture("ahmed@example.com", "Football", "karim@example.com");
        Transaction drill = seededCompletedFixture("youssef@example.com", "Cordless Drill", "omar@example.com");

        assertThat(football.getBorrower().getEmail()).isEqualTo("karim@example.com");
        assertThat(football.getLender().getEmail()).isEqualTo("ahmed@example.com");
        assertThat(football.getCommunity().getName()).isEqualTo("Engineering Office");
        assertThat(football.getPurpose()).isEqualTo("Football match practice");
        assertThat(football.getAgreedDurationDays()).isEqualTo(3);
        assertThat(football.getReservedUnit()).isNull();
        assertThat(football.getOriginalDueAt()).isEqualTo(football.getDueAt());
        assertThat(football.getCompletedAt()).isNotNull();
        assertThat(football.getCompletedAt().isAfter(football.getDueAt())).isFalse();

        assertThat(drill.getBorrower().getEmail()).isEqualTo("omar@example.com");
        assertThat(drill.getLender().getEmail()).isEqualTo("youssef@example.com");
        assertThat(drill.getCommunity().getName()).isEqualTo("Hostel Block B");
        assertThat(drill.getPurpose()).isEqualTo("Wall drilling task");
        assertThat(drill.getAgreedDurationDays()).isEqualTo(4);
        assertThat(drill.getReservedUnit()).isNull();
        assertThat(drill.getOriginalDueAt()).isEqualTo(drill.getDueAt());
        assertThat(drill.getCompletedAt()).isNotNull();
        assertThat(drill.getCompletedAt().isAfter(drill.getDueAt())).isTrue();
    }

    @Test
    void v231SeededLifecycleEventsMatchActors() {
        seedDataInitializer.seed();

        for (Transaction txn : List.of(
                seededCompletedFixture("ahmed@example.com", "Football", "karim@example.com"),
                seededCompletedFixture("youssef@example.com", "Cordless Drill", "omar@example.com"))) {
            List<TransactionEvent> events = transactionEventRepository
                    .findByTransactionIdOrderByCreatedAtAsc(txn.getId()).stream()
                    .sorted(Comparator.comparing(TransactionEvent::getId))
                    .toList();

            assertThat(events).extracting(TransactionEvent::getEventType)
                    .containsExactly(
                            TransactionEventType.REQUEST_APPROVED,
                            TransactionEventType.HANDOVER_SCHEDULED,
                            TransactionEventType.LOAN_STARTED,
                            TransactionEventType.HANDOVER_CONFIRMED,
                            TransactionEventType.RETURN_INITIATED,
                            TransactionEventType.RETURN_REPORTED,
                            TransactionEventType.LOAN_COMPLETED);

            assertThat(events.get(0).getActor().getEmail()).isEqualTo(txn.getLender().getEmail());
            assertThat(events.get(1).getActor().getEmail()).isEqualTo(txn.getLender().getEmail());
            assertThat(events.get(2).getActor().getEmail()).isEqualTo(txn.getLender().getEmail());
            assertThat(events.get(3).getActor().getEmail()).isEqualTo(txn.getBorrower().getEmail());
            assertThat(events.get(4).getActor().getEmail()).isEqualTo(txn.getBorrower().getEmail());
            assertThat(events.get(5).getActor().getEmail()).isEqualTo(txn.getBorrower().getEmail());
            assertThat(events.get(6).getActor().getEmail()).isEqualTo(txn.getLender().getEmail());
        }
    }

    @Test
    void v231SeededSystemMessagesMatchLifecycle() {
        seedDataInitializer.seed();

        for (Transaction txn : List.of(
                seededCompletedFixture("ahmed@example.com", "Football", "karim@example.com"),
                seededCompletedFixture("youssef@example.com", "Cordless Drill", "omar@example.com"))) {
            List<TransactionMessage> systemMessages = transactionMessageRepository
                    .findByTransactionIdAndKind(txn.getId(), MessageKind.SYSTEM).stream()
                    .sorted(Comparator.comparing(TransactionMessage::getId))
                    .toList();

            assertThat(systemMessages).allSatisfy(m -> assertThat(m.getAuthor()).isNull());
            assertThat(systemMessages).extracting(TransactionMessage::getBody)
                    .containsExactly(
                            "Handover scheduled",
                            "Loan started",
                            "Borrower confirmed receipt",
                            "Return initiated",
                            "Handback reported",
                            "Loan completed");
        }
    }

    @Test
    void v231SeededEventsHaveNoDeliveries() {
        seedDataInitializer.seed();

        for (Transaction txn : List.of(
                seededCompletedFixture("ahmed@example.com", "Football", "karim@example.com"),
                seededCompletedFixture("youssef@example.com", "Cordless Drill", "omar@example.com"))) {
            List<TransactionEvent> events = transactionEventRepository
                    .findByTransactionIdOrderByCreatedAtAsc(txn.getId());
            assertThat(events).isNotEmpty();
            for (TransactionEvent event : events) {
                assertThat(transactionEventDeliveryRepository.findByEventId(event.getId()))
                        .as("deliveries for event %s", event.getEventType())
                        .isEmpty();
            }
        }
    }

    @Test
    void v231SeedIsIdempotentForCompletedFixtures() {
        seedDataInitializer.seed();

        List<Transaction> firstCompleted = List.of(
                seededCompletedFixture("ahmed@example.com", "Football", "karim@example.com"),
                seededCompletedFixture("youssef@example.com", "Cordless Drill", "omar@example.com"));
        long eventsAfterFirst = firstCompleted.stream()
                .mapToLong(t -> transactionEventRepository
                        .findByTransactionIdOrderByCreatedAtAsc(t.getId()).size())
                .sum();
        long messagesAfterFirst = firstCompleted.stream()
                .mapToLong(t -> transactionMessageRepository
                        .findByTransactionIdAndKind(t.getId(), MessageKind.SYSTEM).size())
                .sum();

        seedDataInitializer.seed();

        List<Transaction> secondCompleted = List.of(
                seededCompletedFixture("ahmed@example.com", "Football", "karim@example.com"),
                seededCompletedFixture("youssef@example.com", "Cordless Drill", "omar@example.com"));
        assertThat(secondCompleted).extracting(Transaction::getId)
                .containsExactlyInAnyOrderElementsOf(firstCompleted.stream().map(Transaction::getId).toList());
        long eventsAfterSecond = secondCompleted.stream()
                .mapToLong(t -> transactionEventRepository
                        .findByTransactionIdOrderByCreatedAtAsc(t.getId()).size())
                .sum();
        long messagesAfterSecond = secondCompleted.stream()
                .mapToLong(t -> transactionMessageRepository
                        .findByTransactionIdAndKind(t.getId(), MessageKind.SYSTEM).size())
                .sum();
        assertThat(eventsAfterSecond).isEqualTo(eventsAfterFirst);
        assertThat(messagesAfterSecond).isEqualTo(messagesAfterFirst);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Asset findAssetByOwnerAndTitle(User owner, String title) {
        return assetRepository.findByOwnerId(owner.getId()).stream()
                .filter(a -> title.equals(a.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Asset '" + title + "' not found for owner " + owner.getEmail()));
    }
}
