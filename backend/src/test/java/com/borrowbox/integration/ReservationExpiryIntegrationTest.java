package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.TransactionCreateRequest;
import com.borrowbox.dto.TransactionDecisionRequest;
import com.borrowbox.dto.TransactionResponse;
import com.borrowbox.dto.WaitlistEntryResponse;
import com.borrowbox.dto.WaitlistJoinRequest;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.MessageKind;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventDelivery;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.TransactionMessage;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.WaitlistStatus;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.TransactionEventDeliveryRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionMessageRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.repository.WaitlistEntryRepository;
import com.borrowbox.service.TransactionService;
import com.borrowbox.service.WaitlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
 * V2.5.2 stale-reservation expiry against real MySQL.
 *
 * Expiry is opportunistic, so every scenario here drives it the way production
 * does: by starting a create() or a waitlist join() for the affected asset and
 * letting that call run the sweep. There is no scheduler, so no test may wait
 * for a background transition to happen on its own.
 *
 * Lapsing is done by writing a past reservation_expires_at directly rather than
 * by sleeping: the deadline is persisted server-side data, so moving it back is
 * deterministic and keeps the suite fast. No test here sleeps.
 *
 * Single-thread scenarios run in a nested transaction and roll back.
 */
@SpringBootTest
@ActiveProfiles("test")
public class ReservationExpiryIntegrationTest {

    @Autowired private SeedDataInitializer seedDataInitializer;
    @Autowired private TransactionService transactionService;
    @Autowired private WaitlistService waitlistService;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetUnitRepository assetUnitRepository;
    @Autowired private CommunityListingRepository communityListingRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private TransactionEventRepository transactionEventRepository;
    @Autowired private TransactionEventDeliveryRepository transactionEventDeliveryRepository;
    @Autowired private TransactionMessageRepository transactionMessageRepository;

    // ── Helpers ───────────────────────────────────────────────────────

    private User seedUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("missing seed user " + email));
    }

    private Asset football() {
        User ahmed = seedUser("ahmed@example.com");
        return assetRepository.findByOwnerId(ahmed.getId()).stream()
                .filter(a -> a.getTitle().equals("Football"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed asset Football"));
    }

    private CommunityListing footballInCse() {
        return footballIn("CSE Department");
    }

    private CommunityListing footballIn(String communityName) {
        Community community = communityRepository.findAll().stream()
                .filter(c -> c.getName().equals(communityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed community " + communityName));
        Asset football = football();
        return communityListingRepository.findByAssetIdAndCommunityId(football.getId(), community.getId())
                .orElseThrow(() -> new AssertionError("Missing Football listing in " + communityName));
    }

    private long countUnits(Asset asset, AssetUnitStatus status) {
        return assetUnitRepository.findByAssetId(asset.getId()).stream()
                .filter(u -> u.getStatus() == status)
                .count();
    }

    private Transaction reload(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new AssertionError("missing transaction " + id));
    }

    /**
     * Makes the transaction's reservation deadline lapsed. Writing the column
     * directly is the only clock control available without introducing a Clock
     * abstraction, and it is exact.
     */
    private void lapseReservationDeadline(Long transactionId) {
        Transaction txn = reload(transactionId);
        txn.setReservationExpiresAt(LocalDateTime.now().minusHours(1));
        transactionRepository.saveAndFlush(txn);
    }

    private void clearReservationDeadline(Long transactionId) {
        Transaction txn = reload(transactionId);
        txn.setReservationExpiresAt(null);
        transactionRepository.saveAndFlush(txn);
    }

    // ── Deadline stamping is persisted ────────────────────────────────

    @Test
    @Transactional
    void createPersistsAPendingDeadline() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        CommunityListing cseFootball = footballInCse();

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Deadline check " + UUID.randomUUID(), 3, null),
                salah);

        assertThat(created.state()).isEqualTo(TransactionStatus.PENDING);
        // The deadline is an internal scheduling detail and is deliberately not
        // on TransactionResponse, so it is asserted by re-reading the row.
        assertThat(reload(created.id()).getReservationExpiresAt())
                .as("create() stamps a real persisted deadline")
                .isNotNull().isAfter(LocalDateTime.now());
    }

    @Test
    @Transactional
    void aRowWithNoDeadlineIsNeverExpired() {
        // The pre-V2.5.2 row shape: an APPROVED negotiation with a null deadline.
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        CommunityListing cseFootball = footballInCse();

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "No deadline " + UUID.randomUUID(), 3, null),
                salah);
        clearReservationDeadline(created.id());
        assertThat(reload(created.id()).getReservationExpiresAt()).isNull();

        // A sweep for this asset must leave it completely alone.
        transactionService.expireStaleReservations(football().getId());

        assertThat(reload(created.id()).getState()).isEqualTo(TransactionStatus.PENDING);
        assertThat(reload(created.id()).getReservationExpiresAt()).isNull();
        assertThat(created.reservationHeld()).isTrue();
    }

    // ── Expiry releases the unit so it can be claimed again ───────────

    @Test
    @Transactional
    void expiredReservationBecomesAvailableAndIsClaimedByTheNextRequest() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballInCse();
        assertThat(countUnits(football, AssetUnitStatus.AVAILABLE)).isEqualTo(1);

        // Salah takes the only AVAILABLE unit and then abandons it.
        TransactionResponse stale = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Abandoned " + UUID.randomUUID(), 3, null), salah);
        assertThat(countUnits(football, AssetUnitStatus.AVAILABLE)).isEqualTo(0);
        lapseReservationDeadline(stale.id());

        // Youssef's create() runs the sweep first, then claims the freed unit.
        TransactionResponse replacement = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Replacement " + UUID.randomUUID(), 2, null),
                youssef);

        Transaction expired = reload(stale.id());
        assertThat(expired.getState()).isEqualTo(TransactionStatus.EXPIRED);
        assertThat(expired.getReservedUnit()).as("reservation handle released").isNull();
        assertThat(expired.getReservedAt()).as("reservedAt cleared").isNull();
        assertThat(expired.getReservationExpiresAt()).as("deadline cleared").isNull();
        assertThat(expired.getReservationExpiresAt()).isNull();

        assertThat(replacement.state()).isEqualTo(TransactionStatus.PENDING);
        assertThat(replacement.reservationHeld()).isTrue();
        assertThat(countUnits(football, AssetUnitStatus.AVAILABLE)).isEqualTo(0);
        assertThat(countUnits(football, AssetUnitStatus.RESERVED)).isEqualTo(2);
    }

    // ── Expiry promotes the waitlist ──────────────────────────────────

    @Test
    @Transactional
    void expiredReservationPromotesTheHeadWaiter() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballInCse();

        TransactionResponse held = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Held " + UUID.randomUUID(), 3, null), salah);
        waitlistService.join(cseFootball.getId(),
                new WaitlistJoinRequest("Waiting " + UUID.randomUUID(), 2), youssef);
        assertThat(waitlistService.listForBorrower(youssef)).hasSize(1);

        lapseReservationDeadline(held.id());

        // Driving the sweep through create() must also run the existing
        // promotion path, so the freed unit goes straight to the waiter.
        transactionService.expireStaleReservations(football.getId());

        assertThat(reload(held.id()).getState()).isEqualTo(TransactionStatus.EXPIRED);
        List<Transaction> promoted = transactionRepository.findByBorrowerIdOrderByIdDesc(youssef.getId()).stream()
                .filter(t -> t.getState() == TransactionStatus.PENDING)
                .toList();
        assertThat(promoted).as("the waiter was promoted into a PENDING transaction").hasSize(1);
        assertThat(promoted.get(0).getReservedUnit()).isNotNull();
        assertThat(promoted.get(0).getReservationExpiresAt())
                .as("a promoted reservation gets its own deadline")
                .isNotNull().isAfter(LocalDateTime.now());
        assertThat(countUnits(football, AssetUnitStatus.RESERVED)).isEqualTo(2);
    }

    // ── join() runs the sweep before its availability decision ────────

    @Test
    @Transactional
    void joinAfterExpirySeesTheFreedUnitAsAvailable() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        CommunityListing cseFootball = footballInCse();

        TransactionResponse stale = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Abandoned " + UUID.randomUUID(), 3, null), salah);
        lapseReservationDeadline(stale.id());

        // join() sweeps first, which frees the unit, so the availability gate
        // now correctly answers "a unit is available, make a normal request".
        assertThatThrownBy(() -> waitlistService.join(cseFootball.getId(),
                new WaitlistJoinRequest("Should be rejected " + UUID.randomUUID(), 2), youssef))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("currently available");

        assertThat(reload(stale.id()).getState()).isEqualTo(TransactionStatus.EXPIRED);
        assertThat(waitlistService.listForBorrower(youssef)).isEmpty();
    }

    @Test
    @Transactional
    void joinAfterExpiryQueuesOnlyWhenTheFreedUnitWentToAnExistingWaiter() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        User omar = seedUser("omar@example.com");
        CommunityListing cseFootball = footballInCse();
        // Omar is a Hostel member, not a CSE member, so he queues through the
        // Hostel listing. The queue is per Asset and shared across communities,
        // so he lands in the same Football queue as Youssef.
        CommunityListing hostelFootball = footballIn("Hostel Block B");

        TransactionResponse stale = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Abandoned " + UUID.randomUUID(), 3, null), salah);
        // Youssef queues up while the unit is still legitimately held.
        waitlistService.join(cseFootball.getId(),
                new WaitlistJoinRequest("First " + UUID.randomUUID(), 2), youssef);
        lapseReservationDeadline(stale.id());

        // Omar's join sweeps: the unit is freed and handed to Youssef, so by the
        // time the gate runs there is genuinely nothing left to hand out.
        WaitlistEntryResponse omarEntry = waitlistService.join(hostelFootball.getId(),
                new WaitlistJoinRequest("Second " + UUID.randomUUID(), 2), omar);

        assertThat(reload(stale.id()).getState()).isEqualTo(TransactionStatus.EXPIRED);
        assertThat(omarEntry.status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(omarEntry.position()).isEqualTo(1);
        // Youssef's entry is now PROMOTED, not WAITING.
        assertThat(waitlistService.listForBorrower(youssef)).isEmpty();
        assertThat(waitlistEntryRepository.findByBorrowerIdAndStatusOrderByCreatedAtAscIdAsc(
                youssef.getId(), WaitlistStatus.WAITING)).isEmpty();
    }

    // ── Event + message emission ──────────────────────────────────────

    @Test
    @Transactional
    void expiryEmitsSystemMessageAndDeliversTheEventToBothParticipants() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        CommunityListing cseFootball = footballInCse();

        TransactionResponse stale = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Abandoned " + UUID.randomUUID(), 3, null), salah);
        lapseReservationDeadline(stale.id());

        transactionService.expireStaleReservations(football().getId());

        List<TransactionMessage> messages =
                transactionMessageRepository.findByTransactionIdOrderByCreatedAtAsc(stale.id());
        assertThat(messages).anySatisfy(m -> {
            assertThat(m.getKind()).isEqualTo(MessageKind.SYSTEM);
            assertThat(m.getBody()).contains("expired");
        });

        List<TransactionEvent> events =
                transactionEventRepository.findByTransactionIdOrderByCreatedAtAsc(stale.id());
        TransactionEvent expired = events.stream()
                .filter(e -> e.getEventType() == TransactionEventType.EXPIRED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no EXPIRED event emitted"));
        assertThat(expired.getActor()).as("no user caused an expiry").isNull();

        List<TransactionEventDelivery> deliveries =
                transactionEventDeliveryRepository.findByEventId(expired.getId());
        assertThat(deliveries).as("EXPIRED is delivered, not silently dropped").hasSize(2);
        assertThat(deliveries).extracting(d -> d.getRecipient().getId())
                .containsExactlyInAnyOrder(ahmed.getId(), salah.getId());
    }

    // ── RETURN_DISPUTED stays frozen ──────────────────────────────────

    @Test
    @Transactional
    void returnDisputedReservationIsNeverSwept() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballInCse();

        TransactionResponse stale = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Abandoned " + UUID.randomUUID(), 3, null), salah);
        Transaction txn = reload(stale.id());
        // Force the frozen-state shape directly: this slice deliberately does not
        // implement the return-dispute flow, so the state is set rather than
        // driven through a full loan + evidence + dispute sequence.
        txn.setState(TransactionStatus.RETURN_DISPUTED);
        txn.setReservationExpiresAt(LocalDateTime.now().minusDays(30));
        transactionRepository.saveAndFlush(txn);
        AssetUnit held = txn.getReservedUnit();
        held.setStatus(AssetUnitStatus.BORROWED);
        assetUnitRepository.saveAndFlush(held);

        transactionService.expireStaleReservations(football.getId());

        Transaction after = reload(stale.id());
        assertThat(after.getState()).isEqualTo(TransactionStatus.RETURN_DISPUTED);
        assertThat(after.getReservedUnit()).as("the contested reservation handle is retained").isNotNull();
        assertThat(after.getReservedAt()).isNotNull();
        assertThat(after.getReservedUnit().getStatus())
                .as("the disputed unit stays BORROWED").isEqualTo(AssetUnitStatus.BORROWED);
    }

    // ── Exactly-one-wins between a lifecycle decision and a sweep ─────

    /**
     * Approve reaching the row first re-stamps the deadline, which makes the
     * later sweep a no-op. This is the branch the true race below practically
     * never reaches on its own: the sweep's shortlist query is non-locking, so
     * it usually gets to the row lock before approve finishes validating.
     */
    @Test
    @Transactional
    void approveReStampsTheDeadlineSoALaterSweepIsANoOp() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballInCse();
        long availableBefore = countUnits(football, AssetUnitStatus.AVAILABLE);

        TransactionResponse doomed = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Stale but rescued " + UUID.randomUUID(), 3, null), salah);
        lapseReservationDeadline(doomed.id());

        TransactionResponse approved = transactionService.approve(
                doomed.id(), new TransactionDecisionRequest("granted"), ahmed);
        assertThat(approved.state()).isEqualTo(TransactionStatus.APPROVED);

        // The sweep still sees the row in its candidate query (it only filters
        // on deadline < now in SQL), so the under-lock re-check is what saves it.
        transactionService.expireStaleReservations(football.getId());

        Transaction after = reload(doomed.id());
        assertThat(after.getState()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(after.getReservationExpiresAt())
                .as("approve replaces the lapsed deadline instead of clearing it")
                .isAfter(LocalDateTime.now());
        assertThat(countUnits(football, AssetUnitStatus.AVAILABLE))
                .as("a re-stamped reservation keeps its unit")
                .isEqualTo(availableBefore - 1);
    }

    /**
     * The mirror image: once the sweep has expired the row, a late approve must
     * be refused outright rather than reviving a reservation that was already
     * handed back.
     */
    @Test
    @Transactional
    void approveAfterExpiryIsRefusedAndDoesNotReviveTheReservation() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballInCse();
        long availableBefore = countUnits(football, AssetUnitStatus.AVAILABLE);

        TransactionResponse doomed = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Too late " + UUID.randomUUID(), 3, null), salah);
        lapseReservationDeadline(doomed.id());

        transactionService.expireStaleReservations(football.getId());
        assertThat(reload(doomed.id()).getState()).isEqualTo(TransactionStatus.EXPIRED);

        assertThatThrownBy(() -> transactionService.approve(
                doomed.id(), new TransactionDecisionRequest("granted"), ahmed))
                .isInstanceOf(BusinessRuleViolationException.class);

        Transaction after = reload(doomed.id());
        assertThat(after.getState()).isEqualTo(TransactionStatus.EXPIRED);
        assertThat(after.getReservedUnit()).isNull();
        assertThat(countUnits(football, AssetUnitStatus.AVAILABLE))
                .as("the refused approve must not re-reserve the freed unit")
                .isEqualTo(availableBefore);
    }

    /**
     * Real concurrent approve-vs-expiry against the same row. Both racers take
     * the same PESSIMISTIC_WRITE lock and both re-check under it, so the point
     * is invariants rather than a fixed interleaving: no deadlock, no lock
     * timeout, exactly one terminal state, exactly one unit outcome, and no unit
     * referenced by two live transactions.
     *
     * <p>In practice the sweep wins this race every time, because its candidate
     * query is non-locking while approve still has to load and validate the
     * transaction first. The approve-wins branch is therefore covered
     * deterministically by {@link #approveReStampsTheDeadlineSoALaterSweepIsANoOp}
     * and {@link #approveAfterExpiryIsRefusedAndDoesNotReviveTheReservation};
     * this test guards the interleaving, not one particular result.
     *
     * <p>Not @Transactional: each racer needs its own committed transaction, so
     * the test cleans up its own rows and leaves the seeded fixture intact.
     */
    @Test
    void concurrentApproveAndExpiryOnTheSameRowHasExactlyOneWinner() throws Exception {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballInCse();
        String purpose = "Raced expiry " + UUID.randomUUID();

        long availableBefore = countUnits(football, AssetUnitStatus.AVAILABLE);
        long reservedBefore = countUnits(football, AssetUnitStatus.RESERVED);

        TransactionResponse doomed = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), purpose, 3, null), salah);
        // Commit the lapsed deadline so the racers start from a stale row.
        lapseReservationDeadline(doomed.id());

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier startGate = new CyclicBarrier(threads);
        List<Callable<Object>> racers = List.of(
                () -> {
                    startGate.await();
                    return transactionService.approve(
                            doomed.id(), new TransactionDecisionRequest("granted"), ahmed);
                },
                () -> {
                    startGate.await();
                    transactionService.expireStaleReservations(football.getId());
                    return "swept";
                });
        List<Future<Object>> futures = pool.invokeAll(racers);
        for (Future<Object> future : futures) {
            // A loser is allowed to fail cleanly; a deadlock or lock timeout is not.
            try {
                future.get();
            } catch (java.util.concurrent.ExecutionException ex) {
                assertThat(ex.getCause()).isInstanceOf(BusinessRuleViolationException.class);
            }
        }
        pool.shutdown();

        Transaction after = reload(doomed.id());
        assertThat(after.getState())
                .as("exactly one terminal outcome, never a half-applied state")
                .isIn(TransactionStatus.APPROVED, TransactionStatus.EXPIRED);
        assertConsistentOutcome(after, football);

        // create() consumed the asset's only AVAILABLE unit, so the two possible
        // outcomes are exact mirror images: expiry gives the unit back, approve
        // keeps it and re-stamps the deadline. Never a double release, and never
        // a closed transaction still holding a reservation.
        if (after.getState() == TransactionStatus.EXPIRED) {
            assertThat(countUnits(football, AssetUnitStatus.AVAILABLE)).isEqualTo(availableBefore);
            assertThat(countUnits(football, AssetUnitStatus.RESERVED)).isEqualTo(reservedBefore);
        } else {
            assertThat(countUnits(football, AssetUnitStatus.AVAILABLE)).isEqualTo(availableBefore - 1);
            assertThat(countUnits(football, AssetUnitStatus.RESERVED)).isEqualTo(reservedBefore + 1);
            assertThat(after.getReservationExpiresAt()).isAfter(LocalDateTime.now());
        }

        cleanup(football, purpose, reservedBefore);
    }

    private void assertConsistentOutcome(Transaction txn, Asset football) {
        if (txn.getState() == TransactionStatus.EXPIRED) {
            assertThat(txn.getReservedUnit()).as("EXPIRED never keeps a reservation handle").isNull();
            assertThat(txn.getReservedAt()).isNull();
            assertThat(txn.getReservationExpiresAt()).isNull();
        } else {
            assertThat(txn.getReservedUnit()).isNotNull();
            assertThat(txn.getReservedUnit().getStatus()).isEqualTo(AssetUnitStatus.RESERVED);
        }
        // No unit may be referenced by two live transactions.
        List<Transaction> all = transactionRepository.findByAssetIdOrderByIdDesc(football.getId());
        List<Long> holders = all.stream()
                .filter(t -> t.getReservedUnit() != null)
                .map(t -> t.getReservedUnit().getId())
                .toList();
        assertThat(holders).doesNotHaveDuplicates();
    }

    /**
     * Restores the seeded fixture: the raced row and the events/messages it
     * produced are removed and the units are put back where they started.
     *
     * <p>Children are deleted explicitly, in FK order, because no repository in
     * this codebase exposes a {@code deleteByTransactionId}. Only deliveries,
     * events, and messages can exist for this flow: evidence requires an
     * explicit upload, reputation is recorded on completion or dispute, and
     * flags are never touched by create/approve/expire.
     */
    private void cleanup(Asset football, String purpose, long reservedBefore) {
        for (Transaction txn : transactionRepository.findByAssetIdOrderByIdDesc(football.getId())) {
            if (!purpose.equals(txn.getPurpose())) {
                continue;
            }
            for (TransactionEvent event
                    : transactionEventRepository.findByTransactionIdOrderByCreatedAtAsc(txn.getId())) {
                transactionEventDeliveryRepository.deleteAll(
                        transactionEventDeliveryRepository.findByEventId(event.getId()));
            }
            transactionEventRepository.deleteAll(
                    transactionEventRepository.findByTransactionIdOrderByCreatedAtAsc(txn.getId()));
            transactionMessageRepository.deleteAll(
                    transactionMessageRepository.findByTransactionIdOrderByCreatedAtAsc(txn.getId()));

            if (txn.getReservedUnit() != null) {
                AssetUnit unit = txn.getReservedUnit();
                unit.setStatus(AssetUnitStatus.AVAILABLE);
                assetUnitRepository.saveAndFlush(unit);
            }
            transactionRepository.deleteById(txn.getId());
            transactionRepository.flush();
        }
        transactionEventDeliveryRepository.flush();
        transactionEventRepository.flush();
        transactionMessageRepository.flush();
        waitlistEntryRepository.findByAssetId(football.getId()).stream()
                .filter(e -> purpose.equals(e.getPurpose()))
                .forEach(waitlistEntryRepository::delete);
        waitlistEntryRepository.flush();

        assertThat(countUnits(football, AssetUnitStatus.RESERVED))
                .as("cleanup restores the seeded reservation count")
                .isEqualTo(reservedBefore);
    }

    // ── The UNIQUE(reserved_unit_id) backstop still holds ─────────────

    @Test
    @Transactional
    void expiringAndClaimingNeverCreatesTwoReservationsOfTheSameUnit() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        User omar = seedUser("omar@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballInCse();

        TransactionResponse stale = transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Abandoned " + UUID.randomUUID(), 3, null), salah);
        lapseReservationDeadline(stale.id());

        // The sweep may hand the freed unit to a queued waiter; make sure exactly
        // one live transaction can reference any given unit afterwards.
        transactionService.expireStaleReservations(football.getId());
        transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "After " + UUID.randomUUID(), 2, null), youssef);

        assertThat(reload(stale.id()).getState()).isEqualTo(TransactionStatus.EXPIRED);

        List<Transaction> live = List.of(
                transactionRepository.findByBorrowerIdOrderByIdDesc(salah.getId()),
                transactionRepository.findByBorrowerIdOrderByIdDesc(youssef.getId()),
                transactionRepository.findByBorrowerIdOrderByIdDesc(omar.getId())).stream()
                .flatMap(List::stream)
                .filter(t -> t.getReservedUnit() != null)
                .toList();
        long distinctUnits = live.stream().map(t -> t.getReservedUnit().getId()).distinct().count();
        assertThat(distinctUnits)
                .as("UNIQUE(reserved_unit_id) still holds: no unit is referenced twice")
                .isEqualTo(live.size());

        // And the asset is still internally consistent.
        assertThat(countUnits(football, AssetUnitStatus.AVAILABLE)
                + countUnits(football, AssetUnitStatus.RESERVED)
                + countUnits(football, AssetUnitStatus.BORROWED))
                .isEqualTo(assetUnitRepository.findByAssetId(football.getId()).size());
    }
}
