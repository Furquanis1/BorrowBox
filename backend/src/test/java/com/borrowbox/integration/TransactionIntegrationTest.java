package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.CounterOfferRequest;
import com.borrowbox.dto.ListingCreateRequest;
import com.borrowbox.dto.TransactionCreateRequest;
import com.borrowbox.dto.TransactionDecisionRequest;
import com.borrowbox.dto.TransactionResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.TransactionService;
import com.borrowbox.service.CommunityListingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the V2.2.1 transaction negotiation service against real MySQL,
 * including the locked authorization rules, the reservation life-cycle and the
 * exactly-one-wins concurrency race on the shared AssetUnit pool.
 *
 * Single-thread scenarios run in a nested transaction and roll back. The
 * concurrency scenario commits independently and cleans up its own rows so the
 * canonical seeded fixture (2/1/0, one APPROVED backing transaction) survives.
 */
@SpringBootTest
@ActiveProfiles("test")
public class TransactionIntegrationTest {

    private static final String RACE_PURPOSE_PREFIX = "Race: ";

    @Autowired private SeedDataInitializer seedDataInitializer;
    @Autowired private TransactionService transactionService;
    @Autowired private CommunityListingService listingService;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetUnitRepository assetUnitRepository;
    @Autowired private CommunityListingRepository communityListingRepository;
    @Autowired private TransactionRepository transactionRepository;

    // ── Helpers ───────────────────────────────────────────────────────

    private User seedUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("missing seed user " + email));
    }

    private Asset seedAssetOf(User owner, String title) {
        return assetRepository.findByOwnerId(owner.getId()).stream()
                .filter(a -> a.getTitle().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed asset " + title));
    }

    private CommunityListing cseFootballListing(Asset football) {
        return communityListingRepository.findByAssetIdAndCommunityId(football.getId(), cseId())
                .orElseThrow(() -> new AssertionError("Missing Football listing in CSE"));
    }

    private Long cseId() {
        return communityRepository.findAll().stream()
                .filter(c -> c.getName().equals("CSE Department"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed community CSE Department"))
                .getId();
    }

    private long countOf(Asset asset, AssetUnitStatus status) {
        return assetUnitRepository.findByAssetId(asset.getId()).stream()
                .filter(u -> u.getStatus() == status)
                .count();
    }

    // ── Full negotiation flows ────────────────────────────────────────

    @Test
    @Transactional
    void approveWritesAgreedSnapshotAndKeepsReservation() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        long listingId = cseFootballListing(football).getId();

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(listingId, "Practice for the match", 3), salah);
        assertThat(created.state()).isEqualTo(TransactionStatus.PENDING);
        assertThat(created.reservationHeld()).isTrue();
        assertThat(countOf(football, AssetUnitStatus.RESERVED)).isEqualTo(2);

        TransactionResponse approved = transactionService.approve(
                created.id(), new TransactionDecisionRequest("Fine, take it"), ahmed);

        assertThat(approved.state()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(approved.agreedPurpose()).isEqualTo("Practice for the match");
        assertThat(approved.agreedDurationDays()).isEqualTo(3);
        assertThat(approved.agreedAt()).isNotNull();
        assertThat(approved.reservationHeld()).isTrue();
        assertThat(approved.decisionNote()).isEqualTo("Fine, take it");
        assertThat(countOf(football, AssetUnitStatus.RESERVED)).isEqualTo(2);
    }

    @Test
    @Transactional
    void rejectReleasesReservationForNextBorrower() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        long listingId = cseFootballListing(football).getId();

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(listingId, "Sunday game", 2), salah);
        assertThat(countOf(football, AssetUnitStatus.RESERVED)).isEqualTo(2);

        TransactionResponse rejected = transactionService.reject(
                created.id(), new TransactionDecisionRequest("Football is already booked"), ahmed);

        assertThat(rejected.state()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(rejected.reservationHeld()).isFalse();
        assertThat(countOf(football, AssetUnitStatus.RESERVED)).isEqualTo(1);

        TransactionResponse again = transactionService.create(
                new TransactionCreateRequest(listingId, "Sunday game", 2), salah);
        assertThat(again.state()).isEqualTo(TransactionStatus.PENDING);
        assertThat(again.reservationHeld()).isTrue();
    }

    @Test
    @Transactional
    void counterOfferThenAcceptSnapshotsCounterTerms() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        long listingId = cseFootballListing(football).getId();

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(listingId, "Saturday pick-up", 2), salah);

        TransactionResponse countered = transactionService.counterOffer(
                created.id(), new CounterOfferRequest(null, 5, "Saturday is free"), ahmed);

        assertThat(countered.state()).isEqualTo(TransactionStatus.COUNTER_OFFERED);
        assertThat(countered.counterPurpose()).isEqualTo("Saturday pick-up");
        assertThat(countered.counterDurationDays()).isEqualTo(5);
        assertThat(countered.counterNote()).isEqualTo("Saturday is free");
        assertThat(countered.reservationHeld()).isTrue();

        TransactionResponse accepted = transactionService.acceptCounter(countered.id(), salah);
        assertThat(accepted.state()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(accepted.agreedPurpose()).isEqualTo("Saturday pick-up");
        assertThat(accepted.agreedDurationDays()).isEqualTo(5);
        assertThat(accepted.reservationHeld()).isTrue();
    }

    @Test
    @Transactional
    void borrowerCancelReleasesReservation() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        User ahmed = seedUser("ahmed@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        long listingId = cseFootballListing(football).getId();

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(listingId, "Evening kick-about", 1), salah);

        TransactionResponse cancelled = transactionService.cancel(created.id(), salah);
        assertThat(cancelled.state()).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(cancelled.reservationHeld()).isFalse();
        assertThat(countOf(football, AssetUnitStatus.RESERVED)).isEqualTo(1);

        TransactionResponse next = transactionService.create(
                new TransactionCreateRequest(listingId, "Evening kick-about", 1), youssef);
        assertThat(next.state()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @Transactional
    void pendingRequestBlocksSecondBorrowerUntilReleased() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        long listingId = cseFootballListing(football).getId();

        transactionService.create(new TransactionCreateRequest(listingId, "Group practice", 3), salah);

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(listingId, "Group practice", 3), youssef))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("No available unit");

        transactionService.cancel(transactionService.listForBorrower(salah).get(0).id(), salah);

        TransactionResponse third = transactionService.create(
                new TransactionCreateRequest(listingId, "Group practice", 3), youssef);
        assertThat(third.state()).isEqualTo(TransactionStatus.PENDING);
    }

    // ── Locked authorization rules ────────────────────────────────────

    @Test
    @Transactional
    void ownerCannotRequestOwnAsset() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        Asset football = seedAssetOf(ahmed, "Football");

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(cseFootballListing(football).getId(), "Me", 1), ahmed))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("You cannot request your own asset");
    }

    @Test
    @Transactional
    void nonMemberCannotRequest() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        User stranger = new User("Stranger", "stranger." + UUID.randomUUID() + "@example.com");
        stranger.setPasswordHash("x");
        stranger.setStatus(UserStatus.ACTIVE);
        userRepository.save(stranger);

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(cseFootballListing(football).getId(), "Tour", 1), stranger))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @Transactional
    void nonLenderCannotDecide() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        Asset football = seedAssetOf(ahmed, "Football");

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(cseFootballListing(football).getId(), "Warm-up", 1), salah);

        assertThatThrownBy(() -> transactionService.approve(
                created.id(), new TransactionDecisionRequest("approved"), youssef))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> transactionService.reject(
                created.id(), new TransactionDecisionRequest("no"), youssef))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @Transactional
    void decisionOnUnlistedListingIsRejected() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        Community cse = communityRepository.findAll().stream()
                .filter(c -> c.getName().equals("CSE Department"))
                .findFirst().orElseThrow();

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(cseFootballListing(football).getId(), "Temp", 1), salah);

        listingService.unlist(football.getId(), cse.getId(), ahmed);

        assertThatThrownBy(() -> transactionService.approve(
                created.id(), new TransactionDecisionRequest("ok"), ahmed))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("This asset is not currently listed in this community");
    }

    @Test
    @Transactional
    void viewIsScopedToParticipants() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        Asset football = seedAssetOf(ahmed, "Football");

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(cseFootballListing(football).getId(), "View check", 1), salah);

        assertThat(transactionService.view(created.id(), salah).id()).isEqualTo(created.id());
        assertThat(transactionService.view(created.id(), ahmed).id()).isEqualTo(created.id());
        assertThatThrownBy(() -> transactionService.view(created.id(), youssef))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @Transactional
    void seededApprovedTransactionIsTerminalAndNullableEscapeHatch() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Asset football = seedAssetOf(ahmed, "Football");

        AssetUnit reservedUnit = assetUnitRepository.findByAssetId(football.getId()).stream()
                .filter(u -> u.getStatus() == AssetUnitStatus.RESERVED)
                .findFirst().orElseThrow();
        Transaction seeded = transactionRepository.findByReservedUnitId(reservedUnit.getId())
                .orElseThrow();
        assertThat(seeded.getState()).isEqualTo(TransactionStatus.APPROVED);

        assertThatThrownBy(() -> transactionService.approve(
                seeded.getId(), new TransactionDecisionRequest("again"), ahmed))
                .isInstanceOf(BusinessRuleViolationException.class);

        TransactionResponse cancelled = transactionService.cancel(seeded.getId(), salah);
        assertThat(cancelled.state()).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(cancelled.reservationHeld()).isFalse();
    }

    // ── Response hygiene ──────────────────────────────────────────────

    @Test
    @Transactional
    void responsesNeverExposeUnitIds() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Asset football = seedAssetOf(ahmed, "Football");

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(cseFootballListing(football).getId(), "Hygiene", 1), salah);

        String body = List.of(
                created,
                transactionService.view(created.id(), salah),
                transactionService.view(created.id(), ahmed),
                transactionService.approve(created.id(), new TransactionDecisionRequest("ok"), ahmed))
                .toString();

        // The leak signal is the identifier FIELD, not the numeric value: AssetUnit ids
        // and domain ids (communityId, listingId, assetId, ...) are allocated from the
        // same MySQL counter space, so a unit id value can legitimately coincide with
        // another field (e.g. communityId=20). Assert the deterministic contract instead:
        // (1) the response record declares no AssetUnit-identifier field at all, and
        // (2) no serialized response names such a field.
        List<String> declaredFields = Arrays.stream(TransactionResponse.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();
        // 'communityId'/'communityName' legitimately contain the substring "unit"
        // inside "community"; strip that word so only genuine AssetUnit-identifier
        // fields (assetUnit*, reservedUnit*, *unitId, ...) trip the check.
        assertThat(declaredFields).noneMatch(field ->
                field.toLowerCase().replace("community", "").contains("unit"));

        assertThat(body)
                .doesNotContain("reservedUnitId")
                .doesNotContain("assetUnitId")
                .doesNotContain("reservedUnit")
                .doesNotContain("assetUnit");
    }

    // ── Exactly-one-wins concurrency race ─────────────────────────────

    @Test
    void concurrentRequestsProduceExactlyOneReservation() throws Exception {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        Asset football = seedAssetOf(ahmed, "Football");
        long listingId = cseFootballListing(football).getId();
        String racePurpose = RACE_PURPOSE_PREFIX + UUID.randomUUID();

        long reservedBefore = countOf(football, AssetUnitStatus.RESERVED);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier startGate = new CyclicBarrier(threads);
        List<Callable<Object>> racers = new ArrayList<>();
        List<String> borrowerEmails = List.of("salah@example.com", "youssef@example.com");
        for (String email : borrowerEmails) {
            racers.add(() -> {
                startGate.await();
                User borrower = userRepository.findByEmail(email).orElseThrow();
                return transactionService.create(
                        new TransactionCreateRequest(listingId, racePurpose, 3), borrower);
            });
        }

        List<Future<Object>> futures = pool.invokeAll(racers);
        int successes = 0;
        int failures = 0;
        for (Future<Object> future : futures) {
            try {
                TransactionResponse response = (TransactionResponse) future.get();
                assertThat(response.state()).isEqualTo(TransactionStatus.PENDING);
                assertThat(response.reservationHeld()).isTrue();
                successes++;
            } catch (java.util.concurrent.ExecutionException ex) {
                Throwable cause = ex.getCause();
                assertThat(cause).isInstanceOf(BusinessRuleViolationException.class);
                assertThat(cause).hasMessage("No available unit");
                failures++;
            }
        }
        pool.shutdown();

        assertThat(successes).as("exactly one racer wins").isEqualTo(1);
        assertThat(failures).as("exactly one racer loses with a clean 400").isEqualTo(1);
        assertThat(countOf(football, AssetUnitStatus.RESERVED))
                .as("exactly one extra reservation landed")
                .isEqualTo(reservedBefore + 1);

        cleanupRace(football, racePurpose);
    }

    private void cleanupRace(Asset football, String racePurpose) {
        List<AssetUnit> units = assetUnitRepository.findByAssetId(football.getId());
        for (AssetUnit unit : units) {
            if (unit.getStatus() != AssetUnitStatus.RESERVED) {
                continue;
            }
            transactionRepository.findByReservedUnitId(unit.getId()).ifPresent(txn -> {
                if (racePurpose.equals(txn.getPurpose())) {
                    unit.setStatus(AssetUnitStatus.AVAILABLE);
                    assetUnitRepository.save(unit);
                    transactionRepository.deleteById(txn.getId());
                }
            });
        }
        assertThat(countOf(football, AssetUnitStatus.RESERVED))
                .as("race cleanup restores the seeded reservation count")
                .isEqualTo(1);
    }
}