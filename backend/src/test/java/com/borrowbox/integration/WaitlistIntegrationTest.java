package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.TransactionCreateRequest;
import com.borrowbox.dto.TransactionDecisionRequest;
import com.borrowbox.dto.TransactionResponse;
import com.borrowbox.dto.WaitlistEntryResponse;
import com.borrowbox.dto.WaitlistJoinRequest;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.MessageKind;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionMessage;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.WaitlistEntry;
import com.borrowbox.entity.WaitlistStatus;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
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
 * V2.2.7 waitlist integration tests against real MySQL.
 *
 * Canonical seed facts the fixtures rely on:
 *  - Football (Ahmed): 2 units, 1 AVAILABLE + 1 RESERVED (RESERVED backed by
 *    Salah's APPROVED "Football match practice" in CSE). Communities are
 *    "CSE Department", "Hostel Block B" and "Engineering Office".
 *  - youssef: CSE + Hostel. omar: Hostel + Office. salah: CSE. karim: Office.
 *  - Football is listed in CSE, Hostel Block B and Engineering Office.
 *
 * Single-thread scenarios run in a nested transaction and roll back. The
 * concurrency scenario commits independently and cleans up its own rows so
 * the canonical seeded fixture survives.
 */
@SpringBootTest
@ActiveProfiles("test")
public class WaitlistIntegrationTest {

    @Autowired private SeedDataInitializer seedDataInitializer;
    @Autowired private WaitlistService waitlistService;
    @Autowired private TransactionService transactionService;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetUnitRepository assetUnitRepository;
    @Autowired private CommunityListingRepository communityListingRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private TransactionMessageRepository transactionMessageRepository;

    private User seedUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("missing seed user " + email));
    }

    private Community communityNamed(String name) {
        return communityRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing community " + name));
    }

    private Asset football() {
        return assetRepository.findByOwnerId(seedUser("ahmed@example.com").getId())
                .stream().filter(a -> a.getTitle().equals("Football")).findFirst().orElseThrow();
    }

    private CommunityListing footballListingIn(String communityName) {
        Community community = communityNamed(communityName);
        return communityListingRepository.findByAssetIdAndCommunityId(football().getId(), community.getId())
                .orElseThrow(() -> new AssertionError("missing Football listing in " + communityName));
    }

    private long countUnits(Asset asset, AssetUnitStatus status) {
        return assetUnitRepository.findByAssetId(asset.getId()).stream()
                .filter(u -> u.getStatus() == status).count();
    }

    /**
     * Football's only AVAILABLE unit is reserved by a throwaway PENDING request
     * from salah (CSE member), bringing the asset to 0 AVAILABLE so a waitlist
     * join is accepted. Returns that transaction; rejecting it later causes a
     * release that can promote a waiter.
     */
    private TransactionResponse reserveFootballAvailableUnit(Asset football, CommunityListing cseFootball, User salah) {
        assertThat(countUnits(football, AssetUnitStatus.AVAILABLE)).isEqualTo(1);
        return transactionService.create(
                new TransactionCreateRequest(cseFootball.getId(), "Buffer " + UUID.randomUUID(), 1), salah);
    }

    // ── join + read ───────────────────────────────────────────────────

    @Test
    @Transactional
    void joinCreatesWaitingEntryAtPosition1() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballListingIn("CSE Department");

        reserveFootballAvailableUnit(football, cseFootball, salah);
        WaitlistEntryResponse response = waitlistService.join(
                cseFootball.getId(),
                new WaitlistJoinRequest("Football match practice", 3),
                youssef);

        assertThat(response.status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(response.position()).isEqualTo(1);
        assertThat(response.assetId()).isEqualTo(football.getId());
        assertThat(response.communityId()).isEqualTo(cseFootball.getCommunity().getId());

        List<WaitlistEntryResponse> list = waitlistService.listForBorrower(youssef);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).position()).isEqualTo(1);
    }

    // ── ordering (shared queue across listings) ───────────────────────

    @Test
    @Transactional
    void twoWaitersShowOrderedPositions() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        User omar = seedUser("omar@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballListingIn("CSE Department");
        CommunityListing hostelFootball = footballListingIn("Hostel Block B");

        reserveFootballAvailableUnit(football, cseFootball, salah);
        waitlistService.join(cseFootball.getId(),
                new WaitlistJoinRequest("First", 2), youssef);
        waitlistService.join(hostelFootball.getId(),
                new WaitlistJoinRequest("Second", 2), omar);

        assertThat(waitlistService.listForBorrower(youssef).get(0).position()).isEqualTo(1);
        assertThat(waitlistService.listForBorrower(omar).get(0).position()).isEqualTo(2);
    }

    @Test
    @Transactional
    void joinViaDifferentListingsSharesQueue() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        User omar = seedUser("omar@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballListingIn("CSE Department");
        CommunityListing hostelFootball = footballListingIn("Hostel Block B");

        reserveFootballAvailableUnit(football, cseFootball, salah);
        waitlistService.join(cseFootball.getId(),
                new WaitlistJoinRequest("Football match practice", 3), youssef);
        waitlistService.join(hostelFootball.getId(),
                new WaitlistJoinRequest("Football match practice", 2), omar);

        List<WaitlistEntryResponse> youssefList = waitlistService.listForBorrower(youssef);
        List<WaitlistEntryResponse> omarList = waitlistService.listForBorrower(omar);
        assertThat(youssefList.get(0).position()).isEqualTo(1);
        assertThat(omarList.get(0).position()).isEqualTo(2);
        assertThat(youssefList.get(0).assetId()).isEqualTo(football.getId());
        assertThat(omarList.get(0).assetId()).isEqualTo(football.getId());
        assertThat(youssefList.get(0).listingId()).isEqualTo(cseFootball.getId());
        assertThat(omarList.get(0).listingId()).isEqualTo(hostelFootball.getId());
    }

    // ── leave + rejoin ────────────────────────────────────────────────

    @Test
    @Transactional
    void leaveDeletesAndRejoinCreatesNewEntry() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballListingIn("CSE Department");

        reserveFootballAvailableUnit(football, cseFootball, salah);
        WaitlistEntryResponse joined = waitlistService.join(
                cseFootball.getId(),
                new WaitlistJoinRequest("Football match practice", 3),
                youssef);

        WaitlistEntryResponse left = waitlistService.leave(joined.id(), youssef);
        assertThat(left.status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(waitlistService.listForBorrower(youssef)).isEmpty();

        WaitlistEntryResponse rejoined = waitlistService.join(
                cseFootball.getId(),
                new WaitlistJoinRequest("Football match practice", 3),
                youssef);
        assertThat(rejoined.status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(rejoined.id()).isNotEqualTo(joined.id());
    }

    @Test
    @Transactional
    void leaveRejectsNonOwnedEntry() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        User omar = seedUser("omar@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballListingIn("CSE Department");

        reserveFootballAvailableUnit(football, cseFootball, salah);
        WaitlistEntryResponse youssefEntry = waitlistService.join(
                cseFootball.getId(),
                new WaitlistJoinRequest("Football match practice", 3),
                youssef);

        assertThatThrownBy(() -> waitlistService.leave(youssefEntry.id(), omar))
                .isInstanceOf(com.borrowbox.exception.ResourceNotFoundException.class);
    }

    // ── promotion via release ─────────────────────────────────────────

    @Test
    @Transactional
    void rejectReleasesUnitAndPromotesHeadWaiter() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballListingIn("CSE Department");

        TransactionResponse buffer = reserveFootballAvailableUnit(football, cseFootball, salah);

        WaitlistEntryResponse waitResponse = waitlistService.join(
                cseFootball.getId(),
                new WaitlistJoinRequest("Football match practice", 3),
                youssef);
        assertThat(waitResponse.position()).isEqualTo(1);

        // Rejecting the buffer frees the unit and promotes the head waiter.
        transactionService.reject(buffer.id(),
                new TransactionDecisionRequest("Releasing for the waitlist"), ahmed);

        Transaction promoted = transactionRepository.findByAssetIdOrderByIdDesc(football.getId())
                .stream()
                .filter(t -> t.getBorrower().getId().equals(youssef.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(promoted.getState()).isEqualTo(TransactionStatus.PENDING);
        assertThat(promoted.getReservedUnit()).isNotNull();
        assertThat(promoted.getReservedUnit().getStatus()).isEqualTo(AssetUnitStatus.RESERVED);
        assertThat(promoted.getPurpose()).isEqualTo("Football match practice");

        List<TransactionMessage> events = transactionMessageRepository
                .findByTransactionIdAndKind(promoted.getId(), MessageKind.SYSTEM);
        assertThat(events).extracting(TransactionMessage::getBody)
                .contains("Promoted from waitlist");

        List<WaitlistEntry> entries = waitlistEntryRepository.findByAssetId(football.getId())
                .stream().filter(e -> e.getBorrower().getId().equals(youssef.getId())).toList();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(entries.get(0).getPromotedAt()).isNotNull();

        assertThat(waitlistService.listForBorrower(youssef)).isEmpty();
    }

    // ── join guards ───────────────────────────────────────────────────

    @Test
    @Transactional
    void joinRejectsWhenAssetHasAvailableUnit() {
        seedDataInitializer.seed();
        User youssef = seedUser("youssef@example.com");
        CommunityListing cseFootball = footballListingIn("CSE Department");
        // Football still has its 1 AVAILABLE unit → join is rejected.
        waitlistServiceJoinRejected(cseFootball.getId(), youssef);
    }

    @Test
    @Transactional
    void joinRejectsDuplicateOnSameAsset() {
        seedDataInitializer.seed();
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballListingIn("CSE Department");

        reserveFootballAvailableUnit(football, cseFootball, salah);
        waitlistService.join(cseFootball.getId(),
                new WaitlistJoinRequest("Football match practice", 3), youssef);

        assertThatThrownBy(() -> waitlistService.join(
                cseFootball.getId(),
                new WaitlistJoinRequest("Football match practice", 3),
                youssef))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already have a waitlist position");
    }

    private void waitlistServiceJoinRejected(Long listingId, User user) {
        assertThatThrownBy(() -> waitlistService.join(
                listingId,
                new WaitlistJoinRequest("Football match practice", 3),
                user))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("submit a normal request instead");
    }

    // ── concurrency: two releases can only promote one waiter ─────────

    @Test
    void concurrentReleasesProduceExactlyOnePromotion() throws Exception {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        User youssef = seedUser("youssef@example.com");
        User omar = seedUser("omar@example.com");
        Asset football = football();
        CommunityListing cseFootball = footballListingIn("CSE Department");
        CommunityListing hostelFootball = footballListingIn("Hostel Block B");

        // Reserve the last AVAILABLE unit (committed) so joins are accepted.
        TransactionResponse buffer = reserveFootballAvailableUnit(football, cseFootball, salah);

        // Two waiters, committed independently (no outer transaction).
        waitlistService.join(cseFootball.getId(),
                new WaitlistJoinRequest("Race " + UUID.randomUUID(), 3), youssef);
        waitlistService.join(hostelFootball.getId(),
                new WaitlistJoinRequest("Race " + UUID.randomUUID(), 3), omar);

        // Two threads race to reject the SAME buffer transaction. The locked
        // findByIdForUpdate serialises them: exactly one reject wins and that
        // single released unit promotes exactly one waiter.
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier startGate = new CyclicBarrier(threads);
        List<Callable<Void>> racers = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            racers.add(() -> {
                startGate.await();
                try {
                    transactionService.reject(buffer.id(),
                            new TransactionDecisionRequest("Race release"), ahmed);
                } catch (BusinessRuleViolationException ex) {
                    // losing racer gets a clean forward-only violation
                }
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(racers);
        pool.shutdown();
        for (Future<Void> future : futures) {
            future.get();
        }

        assertThat(countUnits(football, AssetUnitStatus.AVAILABLE)).isEqualTo(0);
        assertThat(countUnits(football, AssetUnitStatus.RESERVED)).isEqualTo(2);

        List<WaitlistEntry> raceEntries = waitlistEntryRepository.findByAssetId(football.getId());
        assertThat(raceEntries).hasSize(2);
        assertThat(raceEntries.stream()
                .filter(e -> e.getStatus() == WaitlistStatus.PROMOTED).toList()).hasSize(1);
        assertThat(raceEntries.stream()
                .filter(e -> e.getStatus() == WaitlistStatus.WAITING).toList()).hasSize(1);

        WaitlistEntry promotedEntry = raceEntries.stream()
                .filter(e -> e.getStatus() == WaitlistStatus.PROMOTED).findFirst().orElseThrow();
        Transaction promotedTxn = transactionRepository.findByAssetIdOrderByIdDesc(football.getId())
                .stream()
                .filter(t -> t.getState() == TransactionStatus.PENDING
                        && t.getBorrower().getId().equals(promotedEntry.getBorrower().getId()))
                .findFirst()
                .orElseThrow();
        assertThat(promotedTxn.getState()).isEqualTo(TransactionStatus.PENDING);

        // Cleanup, leaving the canonical Football intact:
        //   1. Leave the still-waiting entry first (its leave-triggered
        //      promoteForAsset finds no AVAILABLE unit yet → no-op).
        //   2. Reject the promoted txn → unit AVAILABLE → promoteForAsset finds
        //      no remaining WAITING entries → no cascade.
        //   3. Delete all race waitlist rows. Rejected transaction history rows
        //      are left in place (they hold no units and are harmless).
        //   4. Football is then back to 1 AVAILABLE + 1 RESERVED (canonical).
        WaitlistEntry stillWaiting = raceEntries.stream()
                .filter(e -> e.getStatus() == WaitlistStatus.WAITING).findFirst().orElseThrow();
        waitlistService.leave(stillWaiting.getId(),
                userRepository.findById(stillWaiting.getBorrower().getId()).orElseThrow());

        transactionService.reject(promotedTxn.getId(),
                new TransactionDecisionRequest("Cleanup"), ahmed);

        waitlistEntryRepository.findByAssetId(football.getId())
                .forEach(e -> waitlistEntryRepository.deleteById(e.getId()));

        assertThat(countUnits(football, AssetUnitStatus.AVAILABLE)).isEqualTo(1);
        assertThat(countUnits(football, AssetUnitStatus.RESERVED)).isEqualTo(1);
        assertThat(waitlistEntryRepository.findByAssetId(football.getId())).isEmpty();
    }
}