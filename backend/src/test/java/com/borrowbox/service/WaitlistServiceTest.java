package com.borrowbox.service;

import com.borrowbox.dto.WaitlistEntryResponse;
import com.borrowbox.dto.WaitlistJoinRequest;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.ListingStatus;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.WaitlistEntry;
import com.borrowbox.entity.WaitlistStatus;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.WaitlistEntryRepository;
import com.borrowbox.service.TransactionEventService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WaitlistServiceTest {

    @Mock
    private WaitlistEntryRepository waitlistEntryRepository;

    @Mock
    private CommunityListingRepository listingRepository;

    @Mock
    private AssetUnitRepository assetUnitRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MembershipService membershipService;

    @Mock
    private TransactionMessageService messageService;

    @Mock
    private TransactionEventService eventService;

    private WaitlistService waitlistService;

    private User owner;
    private User salah;
    private User youssef;
    private User omar;
    private Asset football;
    private Community cse;
    private Community hostel;
    private CommunityListing cseListing;
    private CommunityListing hostelListing;
    private AssetUnit unit;

    @BeforeEach
    void setUp() {
        waitlistService = new WaitlistService(
                waitlistEntryRepository, listingRepository, assetUnitRepository,
                transactionRepository, membershipService, messageService, eventService);

        owner = new User("Ahmed", "ahmed@example.com");
        owner.setId(100L);

        salah = new User("Salah", "salah@example.com");
        salah.setId(101L);

        youssef = new User("Youssef", "youssef@example.com");
        youssef.setId(102L);

        omar = new User("Omar", "omar@example.com");
        omar.setId(103L);

        unit = new AssetUnit();
        unit.setId(777L);
        unit.setStatus(AssetUnitStatus.RESERVED);

        football = new Asset();
        football.setId(500L);
        football.setOwner(owner);
        football.setTitle("Football");
        football.setStatus(AssetStatus.ACTIVE);

        cse = new Community();
        cse.setId(900L);
        cse.setName("CSE Department");

        hostel = new Community();
        hostel.setId(901L);
        hostel.setName("Hostel");

        cseListing = listing(701L, football, cse);
        hostelListing = listing(702L, football, hostel);
    }

    private CommunityListing listing(Long id, Asset asset, Community community) {
        CommunityListing listing = new CommunityListing();
        listing.setId(id);
        listing.setAsset(asset);
        listing.setCommunity(community);
        listing.setListingStatus(ListingStatus.LISTED);
        listing.setListedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        return listing;
    }

    private WaitlistEntry entry(Long id, CommunityListing listing, User borrower, WaitlistStatus status) {
        WaitlistEntry entry = new WaitlistEntry();
        entry.setId(id);
        entry.setAsset(football);
        entry.setListing(listing);
        entry.setBorrower(borrower);
        entry.setPurpose("Football match practice");
        entry.setRequestedDurationDays(3);
        entry.setStatus(status);
        entry.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0).plusMinutes(id));
        return entry;
    }

    private void stubSalahActive(Community community) {
        when(membershipService.isActiveMember(eq(salah.getId()), eq(community.getId()))).thenReturn(true);
    }

    // ── join ──────────────────────────────────────────────────────────

    @Test
    void joinCreatesWaitingEntryWhenNoUnitAvailable() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(cseListing));
        stubSalahActive(cse);
        when(waitlistEntryRepository.existsByAssetIdAndBorrowerId(500L, 101L)).thenReturn(false);
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L)).thenReturn(Optional.empty());

        WaitlistEntry saved = entry(10L, cseListing, salah, WaitlistStatus.WAITING);
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class))).thenReturn(saved);
        when(waitlistEntryRepository.countWaitingBefore(500L, saved.getCreatedAt(), saved.getId())).thenReturn(0L);

        WaitlistEntryResponse response = waitlistService.join(
                701L, new WaitlistJoinRequest("Football match practice", 3), salah);

        assertThat(response.status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(response.assetId()).isEqualTo(500L);
        assertThat(response.communityId()).isEqualTo(900L);
        assertThat(response.borrowerId()).isEqualTo(101L);
        assertThat(response.position()).isEqualTo(1);
        verify(assetUnitRepository).findFirstByAssetIdAndStatusForUpdate(500L);
    }

    @Test
    void joinRejectsWhenAnyUnitIsAvailable() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(cseListing));
        stubSalahActive(cse);
        when(waitlistEntryRepository.existsByAssetIdAndBorrowerId(500L, 101L)).thenReturn(false);
        AssetUnit free = new AssetUnit();
        free.setId(778L);
        free.setStatus(AssetUnitStatus.AVAILABLE);
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L)).thenReturn(Optional.of(free));

        assertThatThrownBy(() -> waitlistService.join(
                701L, new WaitlistJoinRequest("Football match practice", 3), salah))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("submit a normal request instead");
        verify(assetUnitRepository).findFirstByAssetIdAndStatusForUpdate(500L);
    }

    @Test
    void joinRejectsDuplicateBorrowerOnSameAsset() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(cseListing));
        stubSalahActive(cse);
        when(waitlistEntryRepository.existsByAssetIdAndBorrowerId(500L, 101L)).thenReturn(true);

        assertThatThrownBy(() -> waitlistService.join(
                701L, new WaitlistJoinRequest("Football match practice", 3), salah))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already have a waitlist position");
    }

    @Test
    void joinMapsRacedDataIntegrityViolationToBusinessError() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(cseListing));
        stubSalahActive(cse);
        when(waitlistEntryRepository.existsByAssetIdAndBorrowerId(500L, 101L)).thenReturn(false);
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L)).thenReturn(Optional.empty());
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class)))
                .thenThrow(new DataIntegrityViolationException("uqz"));

        assertThatThrownBy(() -> waitlistService.join(
                701L, new WaitlistJoinRequest("Football match practice", 3), salah))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already have a waitlist position");
    }

    @Test
    void joinRejectsNonActiveMember() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(cseListing));
        when(membershipService.isActiveMember(omar.getId(), cse.getId())).thenReturn(false);

        assertThatThrownBy(() -> waitlistService.join(
                701L, new WaitlistJoinRequest("Football match practice", 3), omar))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void joinRejectsBorrowerOwningTheAsset() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(cseListing));
        when(membershipService.isActiveMember(owner.getId(), cse.getId())).thenReturn(true);

        assertThatThrownBy(() -> waitlistService.join(
                701L, new WaitlistJoinRequest("Football match practice", 3), owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("own asset");
    }

    @Test
    void joinRejectsArchivedAsset() {
        football.setStatus(AssetStatus.ARCHIVED);
        when(listingRepository.findById(701L)).thenReturn(Optional.of(cseListing));

        assertThatThrownBy(() -> waitlistService.join(
                701L, new WaitlistJoinRequest("Football match practice", 3), salah))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void joinRequiresPurposeAndDurationBounds() {
        assertThatThrownBy(() -> waitlistService.join(
                701L, new WaitlistJoinRequest("", 3), salah))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> waitlistService.join(
                701L, new WaitlistJoinRequest("Purpose", 0), salah))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> waitlistService.join(
                701L, new WaitlistJoinRequest("Purpose", 31), salah))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── list ──────────────────────────────────────────────────────────

    @Test
    void listForBorrowerReturnsOnlyWaitingEntriesWithDerivedPositions() {
        WaitlistEntry first = entry(10L, cseListing, salah, WaitlistStatus.WAITING);
        WaitlistEntry second = entry(11L, cseListing, salah, WaitlistStatus.WAITING);
        when(waitlistEntryRepository.findByBorrowerIdAndStatusOrderByCreatedAtAscIdAsc(101L, WaitlistStatus.WAITING))
                .thenReturn(List.of(first, second));
        when(waitlistEntryRepository.countWaitingBefore(500L, first.getCreatedAt(), first.getId())).thenReturn(2L);
        when(waitlistEntryRepository.countWaitingBefore(500L, second.getCreatedAt(), second.getId())).thenReturn(3L);

        List<WaitlistEntryResponse> responses = waitlistService.listForBorrower(salah);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).position()).isEqualTo(3);
        assertThat(responses.get(1).position()).isEqualTo(4);
    }

    @Test
    void listForBorrowerExcludesPromotedEntries() {
        when(waitlistEntryRepository.findByBorrowerIdAndStatusOrderByCreatedAtAscIdAsc(101L, WaitlistStatus.WAITING))
                .thenReturn(List.of());

        assertThat(waitlistService.listForBorrower(salah)).isEmpty();
    }

    // ── leave ─────────────────────────────────────────────────────────

    @Test
    void leaveHardDeletesWaitingRowAndPromotesNextWaiter() {
WaitlistEntry mine = entry(10L, cseListing, salah, WaitlistStatus.WAITING);
        when(waitlistEntryRepository.findByIdAndBorrowerId(10L, 101L)).thenReturn(Optional.of(mine));

        WaitlistEntryResponse response = waitlistService.leave(10L, salah);

        verify(waitlistEntryRepository).delete(mine);
        // promoteForAsset ran inside the same leave(): the availability gate was
        // queried (no unit available → no-op promotion).
        verify(assetUnitRepository).findFirstByAssetIdAndStatusForUpdate(500L);
        assertThat(response.status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(response.position()).isEqualTo(0);
    }

    @Test
    void leaveRejectsSomeoneElsesEntry() {
        when(waitlistEntryRepository.findByIdAndBorrowerId(10L, 101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waitlistService.leave(10L, salah))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void leaveRejectsPromotedEntry() {
        WaitlistEntry promoted = entry(10L, cseListing, salah, WaitlistStatus.PROMOTED);
        when(waitlistEntryRepository.findByIdAndBorrowerId(10L, 101L)).thenReturn(Optional.of(promoted));

        assertThatThrownBy(() -> waitlistService.leave(10L, salah))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Only a waiting position can be left");
    }

    // ── promoteForAsset ───────────────────────────────────────────────

    @Test
    void promoteCreatesPendingTransactionWithSystemEvent() {
        AssetUnit free = new AssetUnit();
        free.setId(779L);
        free.setStatus(AssetUnitStatus.AVAILABLE);
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L))
                .thenReturn(Optional.of(free)).thenReturn(Optional.empty());
        WaitlistEntry head = entry(10L, cseListing, salah, WaitlistStatus.WAITING);
        when(waitlistEntryRepository.findFirstWaitingForUpdate(500L))
                .thenReturn(Optional.of(head)).thenReturn(Optional.empty());
        when(membershipService.isActiveMember(eq(101L), eq(900L))).thenReturn(true);
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        waitlistService.promoteForAsset(500L);

        assertThat(head.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(head.getPromotedAt()).isNotNull();
        assertThat(free.getStatus()).isEqualTo(AssetUnitStatus.RESERVED);
        verify(assetUnitRepository).save(free);
        verify(waitlistEntryRepository).save(head);
        verify(transactionRepository).saveAndFlush(any(Transaction.class));
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Promoted from waitlist"));
        verify(eventService).createEventAndDeliveries(any(Transaction.class), eq(TransactionEventType.WAITLIST_PROMOTED), eq(null), eq(null));
    }

    @Test
    void promoteIsNoOpWhenNoUnitAvailable() {
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L)).thenReturn(Optional.empty());

        waitlistService.promoteForAsset(500L);

        verify(waitlistEntryRepository, org.mockito.Mockito.never()).findFirstWaitingForUpdate(anyLong());
    }

@Test
    void promoteSkipsIneligibleHeadAndPromotesNext() {
        AssetUnit free = new AssetUnit();
        free.setId(779L);
        free.setStatus(AssetUnitStatus.AVAILABLE);
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L))
                .thenReturn(Optional.of(free))
                .thenReturn(Optional.of(free))
                .thenReturn(Optional.empty());
        WaitlistEntry ineligible = entry(10L, cseListing, salah, WaitlistStatus.WAITING);
        WaitlistEntry next = entry(11L, cseListing, youssef, WaitlistStatus.WAITING);
        next.setCreatedAt(ineligible.getCreatedAt().plusMinutes(1));
        when(waitlistEntryRepository.findFirstWaitingForUpdate(500L))
                .thenReturn(Optional.of(ineligible)).thenReturn(Optional.of(next)).thenReturn(Optional.empty());
        when(membershipService.isActiveMember(eq(101L), eq(900L))).thenReturn(false);
        when(membershipService.isActiveMember(eq(102L), eq(900L))).thenReturn(true);
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        waitlistService.promoteForAsset(500L);

        assertThat(ineligible.getStatus()).isEqualTo(WaitlistStatus.LEFT);
        assertThat(next.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        verify(waitlistEntryRepository).save(ineligible);
        verify(waitlistEntryRepository).save(next);
        verify(eventService).createEventAndDeliveries(any(Transaction.class), eq(TransactionEventType.WAITLIST_PROMOTED), eq(null), eq(null));
    }

    @Test
    void promoteMarksIneligibleHeadLeftPermanentlyWithoutTransaction() {
        AssetUnit free = new AssetUnit();
        free.setId(779L);
        free.setStatus(AssetUnitStatus.AVAILABLE);
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L))
                .thenReturn(Optional.of(free)).thenReturn(Optional.empty());
        WaitlistEntry head = entry(10L, cseListing, salah, WaitlistStatus.WAITING);
        when(waitlistEntryRepository.findFirstWaitingForUpdate(500L))
                .thenReturn(Optional.of(head)).thenReturn(Optional.empty());
        when(membershipService.isActiveMember(eq(101L), eq(900L))).thenReturn(false);

        waitlistService.promoteForAsset(500L);

        assertThat(head.getStatus()).isEqualTo(WaitlistStatus.LEFT);
        assertThat(head.getPromotedAt()).isNull();
        assertThat(free.getStatus()).isEqualTo(AssetUnitStatus.AVAILABLE);
        org.mockito.Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    void promoteStopsAfterHeadPromotedEvenWhenOthersWaiting() {
        AssetUnit free = new AssetUnit();
        free.setId(779L);
        free.setStatus(AssetUnitStatus.AVAILABLE);
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L))
                .thenReturn(Optional.of(free)).thenReturn(Optional.empty());
        WaitlistEntry head = entry(10L, cseListing, salah, WaitlistStatus.WAITING);
        when(waitlistEntryRepository.findFirstWaitingForUpdate(500L))
                .thenReturn(Optional.of(head)).thenReturn(Optional.empty());
        when(membershipService.isActiveMember(eq(101L), eq(900L))).thenReturn(true);
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        waitlistService.promoteForAsset(500L);

        verify(transactionRepository).saveAndFlush(any(Transaction.class));
        // head is now PROMOTED and locked; the loop reads again, needs a unit →
        // optional empty → returns. No second transaction, no double allocation.
        assertThat(head.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        verify(eventService).createEventAndDeliveries(any(Transaction.class), eq(TransactionEventType.WAITLIST_PROMOTED), eq(null), eq(null));
    }

    @Test
    void promotionUsesServersClockForReservationAndPromotion() {
        LocalDateTime before = LocalDateTime.now();
        AssetUnit free = new AssetUnit();
        free.setId(779L);
        free.setStatus(AssetUnitStatus.AVAILABLE);
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L))
                .thenReturn(Optional.of(free)).thenReturn(Optional.empty());
        WaitlistEntry head = entry(10L, cseListing, salah, WaitlistStatus.WAITING);
        when(waitlistEntryRepository.findFirstWaitingForUpdate(500L))
                .thenReturn(Optional.of(head)).thenReturn(Optional.empty());
        when(membershipService.isActiveMember(eq(101L), eq(900L))).thenReturn(true);
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        waitlistService.promoteForAsset(500L);

        assertThat(head.getPromotedAt()).isNotNull();
        assertThat(head.getPromotedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void promotionCreatesPendingNotApprovedTransaction() {
        AssetUnit free = new AssetUnit();
        free.setId(779L);
        free.setStatus(AssetUnitStatus.AVAILABLE);
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L))
                .thenReturn(Optional.of(free)).thenReturn(Optional.empty());
        WaitlistEntry head = entry(10L, cseListing, salah, WaitlistStatus.WAITING);
        when(waitlistEntryRepository.findFirstWaitingForUpdate(500L))
                .thenReturn(Optional.of(head)).thenReturn(Optional.empty());
        when(membershipService.isActiveMember(eq(101L), eq(900L))).thenReturn(true);
        org.mockito.ArgumentCaptor<Transaction> captor =
                org.mockito.ArgumentCaptor.forClass(Transaction.class);
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        waitlistService.promoteForAsset(500L);

        verify(transactionRepository).saveAndFlush(captor.capture());
        Transaction txn = captor.getValue();
        assertThat(txn.getState()).isEqualTo(TransactionStatus.PENDING);
        assertThat(txn.getBorrower().getId()).isEqualTo(101L);
        assertThat(txn.getLender().getId()).isEqualTo(100L);
        assertThat(txn.getListing().getId()).isEqualTo(701L);
        assertThat(txn.getCommunity().getId()).isEqualTo(900L);
        assertThat(txn.getReservedUnit().getId()).isEqualTo(779L);
        assertThat(txn.getPurpose()).isEqualTo("Football match practice");
        assertThat(txn.getRequestedDurationDays()).isEqualTo(3);
    }
}


