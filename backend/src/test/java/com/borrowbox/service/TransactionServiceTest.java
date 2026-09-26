package com.borrowbox.service;

import com.borrowbox.dto.CounterOfferRequest;
import com.borrowbox.dto.EvidenceResponse;
import com.borrowbox.dto.ExtensionRequest;
import com.borrowbox.dto.TransactionCreateRequest;
import com.borrowbox.dto.TransactionDecisionRequest;
import com.borrowbox.dto.TransactionResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.Evidence;
import com.borrowbox.entity.EvidenceType;
import com.borrowbox.entity.ListingStatus;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRuleRepository;
import com.borrowbox.repository.EvidenceRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.service.TransactionEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CommunityListingRepository listingRepository;

    @Mock
    private AssetUnitRepository assetUnitRepository;

    @Mock
    private CommunityRuleRepository communityRuleRepository;

    @Mock
    private MembershipService membershipService;

    @Mock
    private TransactionMessageService messageService;

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private EvidenceStorageService evidenceStorageService;

    @Mock
    private WaitlistService waitlistService;

    @Mock
    private TransactionEventService eventService;

    @Mock
    private ReputationEventService reputationEventService;

    private TransactionService transactionService;

    private User owner;
    private User borrower;
    private Asset football;
    private Community cse;
    private CommunityListing listing;
    private AssetUnit unit;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository, listingRepository, assetUnitRepository,
                communityRuleRepository, membershipService, messageService,
                evidenceRepository, evidenceStorageService, waitlistService,
                eventService, reputationEventService, 5_242_880L);

        owner = new User("Ahmed", "ahmed@example.com");
        owner.setId(100L);

        borrower = new User("Salah", "salah@example.com");
        borrower.setId(101L);

        unit = new AssetUnit();
        unit.setId(777L);
        unit.setStatus(AssetUnitStatus.AVAILABLE);

        football = new Asset();
        football.setId(500L);
        football.setOwner(owner);
        football.setTitle("Football");
        football.setStatus(AssetStatus.ACTIVE);

        cse = new Community();
        cse.setId(900L);
        cse.setName("CSE Department");

        listing = new CommunityListing();
        listing.setId(701L);
        listing.setAsset(football);
        listing.setCommunity(cse);
        listing.setListingStatus(ListingStatus.LISTED);
        listing.setListedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
    }

    private void stubActiveMember() {
        when(membershipService.isActiveMember(eq(101L), eq(900L))).thenReturn(true);
    }

    private void stubAvailableUnit() {
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L))
                .thenReturn(Optional.of(unit));
    }

    private void stubSaveAndFlushReturnsArgument() {
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Transaction pending(AssetUnit reserved) {
        Transaction txn = new Transaction();
        txn.setId(1L);
        txn.setCommunity(cse);
        txn.setListing(listing);
        txn.setAsset(football);
        txn.setBorrower(borrower);
        txn.setLender(owner);
        if (reserved != null) {
            reserved.setStatus(AssetUnitStatus.RESERVED);
        }
        txn.setReservedUnit(reserved);
        txn.setReservedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        txn.setState(TransactionStatus.PENDING);
        txn.setPurpose("Football match practice");
        txn.setRequestedDurationDays(3);
        return txn;
    }

    private Transaction counterOffered(Transaction base) {
        base.setState(TransactionStatus.COUNTER_OFFERED);
        base.setCounterPurpose("Football match practice");
        base.setCounterDurationDays(5);
        base.setCounterNote("Saturday is fine");
        base.setCounterOfferedAt(LocalDateTime.of(2026, 1, 3, 9, 0));
        return base;
    }

    /**
     * V2.2.4: an ACTIVE loan fixture with the accountability clock stamped at
     * "now" and the reserved unit BORROWED. Callers override dueAt/startedAt
     * where a specific derived condition is under test.
     */
    private Transaction active(AssetUnit reserved) {
        Transaction txn = pending(reserved);
        txn.setState(TransactionStatus.ACTIVE);
        txn.setAgreedPurpose("Football match practice");
        txn.setAgreedDurationDays(3);
        txn.setStartedAt(LocalDateTime.now());
        if (reserved != null) {
            reserved.setStatus(AssetUnitStatus.BORROWED);
        }
        return txn;
    }

    /**
     * V2.5.1: drives the real application upload path for the lender's
     * LENDER_HANDOVER photo while the fixture transaction is AWAITING_HANDOVER,
     * so the confirmHandover precondition is genuinely satisfied through the
     * product flow (no direct repository seeding). The transaction id and the
     * transactionRepository.findByIdForUpdate(1L) stub must already be in
     * place before this helper is called.
     */
    private void uploadLenderHandoverEvidence() {
        AtomicReference<Evidence> saved = new AtomicReference<>();
        when(evidenceStorageService.store(any(byte[].class))).thenReturn("11111111-2222-3333-4444-555555555555");
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> {
            Evidence e = inv.getArgument(0);
            e.setId(7L);
            saved.set(e);
            return e;
        });
        when(evidenceRepository.findByTransactionIdAndType(anyLong(), eq(EvidenceType.LENDER_HANDOVER)))
                .thenAnswer(inv -> saved.get() != null ? List.of(saved.get()) : List.of());
        MockMultipartFile file = new MockMultipartFile("file", "handover.png", "image/png", new byte[]{1, 2});
        transactionService.uploadEvidence(1L, EvidenceType.LENDER_HANDOVER, file, null, null, owner);
    }

    private MockMultipartFile imagePhoto() {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});
    }

    // ── create ────────────────────────────────────────────────────────

    @Test
    void createsPendingTransactionWithReservation() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(listing));
        stubActiveMember();
        stubAvailableUnit();
        stubSaveAndFlushReturnsArgument();

        TransactionResponse response = transactionService.create(
                new TransactionCreateRequest(701L, "Football match practice", 3), borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.lenderId()).isEqualTo(100L);
        assertThat(response.borrowerId()).isEqualTo(101L);
        assertThat(response.communityId()).isEqualTo(900L);
        assertThat(response.assetId()).isEqualTo(500L);
        assertThat(response.purpose()).isEqualTo("Football match practice");
        assertThat(response.requestedDurationDays()).isEqualTo(3);
        assertThat(response.reservationHeld()).isTrue();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.RESERVED);
        verify(transactionRepository).saveAndFlush(any(Transaction.class));
    }

    @Test
    void unauthenticatedUserIsRejected() {
        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "Purpose", 3), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void missingListingIsNotFound() {
        when(listingRepository.findById(701L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "Purpose", 3), borrower))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unlistedListingIsRejected() {
        listing.setListingStatus(ListingStatus.UNLISTED);
        when(listingRepository.findById(701L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "Purpose", 3), borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void archivedAssetIsRejected() {
        football.setStatus(AssetStatus.ARCHIVED);
        when(listingRepository.findById(701L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "Purpose", 3), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("An archived asset cannot be requested");
    }

    @Test
    void nonMemberCannotCreateRequest() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(listing));
        when(membershipService.isActiveMember(101L, 900L)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "Purpose", 3), borrower))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void selfBorrowIsRejected() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(listing));
        when(membershipService.isActiveMember(eq(100L), eq(900L))).thenReturn(true);

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "Purpose", 3), owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("You cannot request your own asset");
    }

    @Test
    void blankPurposeIsRejected() {
        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "   ", 3), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Purpose is required");
    }

    @Test
    void invalidDurationIsRejected() {
        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "Purpose", 0), borrower))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "Purpose", 31), borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void noAvailableUnitIsRejected() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(listing));
        stubActiveMember();
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "Purpose", 3), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("No available unit");
    }

    @Test
    void unitLockedAsUnavailableIsRejected() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(listing));
        stubActiveMember();
        unit.setStatus(AssetUnitStatus.RESERVED);
        when(assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(500L))
                .thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> transactionService.create(
                new TransactionCreateRequest(701L, "Purpose", 3), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("No available unit");
    }

    // ── approve ───────────────────────────────────────────────────────

    @Test
    void approveWritesAgreedSnapshotAndKeepsReservation() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.approve(
                1L, new TransactionDecisionRequest("Looks good"), owner);

        assertThat(response.state()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(response.agreedPurpose()).isEqualTo("Football match practice");
        assertThat(response.agreedDurationDays()).isEqualTo(3);
        assertThat(response.agreedAt()).isNotNull();
        assertThat(response.reservationHeld()).isTrue();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.RESERVED);
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.REQUEST_APPROVED), eq(owner), eq(null));
    }

    @Test
    void nonLenderCannotApprove() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.approve(
                1L, new TransactionDecisionRequest("note"), borrower))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void approveOnNonPendingIsRejected() {
        Transaction txn = counterOffered(pending(unit));
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);

        assertThatThrownBy(() -> transactionService.approve(
                1L, new TransactionDecisionRequest("note"), owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void approveWhenListingUnlistedIsRejected() {
        listing.setListingStatus(ListingStatus.UNLISTED);
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);

        assertThatThrownBy(() -> transactionService.approve(
                1L, new TransactionDecisionRequest("note"), owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── reject ────────────────────────────────────────────────────────

    @Test
    void rejectReleasesReservation() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.reject(
                1L, new TransactionDecisionRequest("Not available this week"), owner);

        assertThat(response.state()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(response.reservationHeld()).isFalse();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.AVAILABLE);
        verify(assetUnitRepository).save(unit);
        verify(waitlistService).promoteForAsset(500L);
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.REQUEST_REJECTED), eq(owner), eq(null));
    }

    // ── counter-offer ─────────────────────────────────────────────────

    @Test
    void counterOfferMovesToCounterOffered() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.counterOffer(
                1L, new CounterOfferRequest("Football match practice", 5, "Saturday is fine"), owner);

        assertThat(response.state()).isEqualTo(TransactionStatus.COUNTER_OFFERED);
        assertThat(response.counterPurpose()).isEqualTo("Football match practice");
        assertThat(response.counterDurationDays()).isEqualTo(5);
        assertThat(response.counterNote()).isEqualTo("Saturday is fine");
        assertThat(response.counterOfferedAt()).isNotNull();
        assertThat(response.reservationHeld()).isTrue();
    }

    @Test
    void counterOfferKeepsOriginalPurposeWhenPurposeBlank() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.counterOffer(
                1L, new CounterOfferRequest(null, 5, null), owner);

        assertThat(response.counterPurpose()).isEqualTo("Football match practice");
        assertThat(response.counterDurationDays()).isEqualTo(5);
    }

    @Test
    void counterOfferWithInvalidDurationIsRejected() {
        assertThatThrownBy(() -> transactionService.counterOffer(
                1L, new CounterOfferRequest("Purpose", 31, "note"), owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void nonLenderCannotCounterOffer() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.counterOffer(
                1L, new CounterOfferRequest("Purpose", 5, null), borrower))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── accept counter ────────────────────────────────────────────────

    @Test
    void acceptCounterApprovesWithCounterSnapshot() {
        Transaction txn = counterOffered(pending(unit));
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.acceptCounter(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(response.agreedPurpose()).isEqualTo("Football match practice");
        assertThat(response.agreedDurationDays()).isEqualTo(5);
        assertThat(response.agreedAt()).isNotNull();
        assertThat(response.reservationHeld()).isTrue();
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.REQUEST_APPROVED), eq(borrower), eq(null));
    }

    @Test
    void nonBorrowerCannotAcceptCounter() {
        Transaction txn = counterOffered(pending(unit));
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.acceptCounter(1L, owner))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void acceptCounterOnPendingIsRejected() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.acceptCounter(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── cancel ────────────────────────────────────────────────────────

    @Test
    void borrowerCancelsPendingAndReleasesUnit() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.cancel(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(response.reservationHeld()).isFalse();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.AVAILABLE);
        verify(assetUnitRepository).save(unit);
        verify(waitlistService).promoteForAsset(500L);
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.REQUEST_CANCELLED), eq(borrower), eq(null));
    }

    @Test
    void borrowerCancelsCounterOfferedExplicitly() {
        Transaction txn = counterOffered(pending(unit));
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.cancel(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(response.reservationHeld()).isFalse();
    }

    @Test
    void lenderCannotCancelPending() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.cancel(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void approvedIsForwardOnlyCannotBeCancelled() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.APPROVED);
        txn.setAgreedPurpose("Football match practice");
        txn.setAgreedDurationDays(3);
        txn.setAgreedAt(LocalDateTime.now());
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.cancel(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> transactionService.cancel(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void eitherPartyCanCancelAwaitingHandoverAndReleasesUnit() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.cancel(1L, owner);
        assertThat(response.state()).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(response.reservationHeld()).isFalse();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.AVAILABLE);
        verify(assetUnitRepository).save(unit);
    }

    @Test
    void canceledRejectedTransactionCannotBeCancelled() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.REJECTED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.cancel(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void nonParticipantCannotCancel() {
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(999L);
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.cancel(1L, intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── V2.2.2 loan lifecycle ────────────────────────────────────────

    @Test
    void stageHandoverMovesApprovedToAwaitingHandover() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.APPROVED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.stageHandover(1L, owner);

        assertThat(response.state()).isEqualTo(TransactionStatus.AWAITING_HANDOVER);
        assertThat(response.reservationHeld()).isTrue();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.RESERVED);
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Handover scheduled"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.HANDOVER_SCHEDULED), eq(owner), eq(null));
    }

    @Test
    void eitherPartyCanStageHandover() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.APPROVED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(transactionService.stageHandover(1L, borrower).state())
                .isEqualTo(TransactionStatus.AWAITING_HANDOVER);
    }

    @Test
    void stageHandoverOnNonApprovedIsRejected() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.stageHandover(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void nonParticipantCannotStageHandover() {
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(999L);
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.APPROVED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.stageHandover(1L, intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void confirmHandoverStartsLoanAndFlipsUnitToBorrowed() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        txn.setAgreedDurationDays(3);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        // V2.5.1: lender must capture handover evidence before confirming.
        uploadLenderHandoverEvidence();

        TransactionResponse response = transactionService.confirmHandover(1L, owner);

        assertThat(response.state()).isEqualTo(TransactionStatus.ACTIVE);
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.dueAt()).isNotNull();
        assertThat(response.originalDueAt()).isNotNull();
        assertThat(response.dueAt()).isEqualTo(response.startedAt().plusDays(3));
        assertThat(response.originalDueAt()).isEqualTo(response.dueAt());
        assertThat(response.borrowerConfirmedAt()).isNull();
        assertThat(response.dueSoon()).isFalse();
        assertThat(response.overdue()).isFalse();
        assertThat(response.handoverWindowOpen()).isTrue();
        assertThat(response.reservationHeld()).isTrue();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.BORROWED);
        verify(assetUnitRepository).save(unit);
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Loan started"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.LOAN_STARTED), eq(owner), eq(null));
    }

    @Test
    void borrowerCannotConfirmHandover() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmHandover(1L, borrower))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void confirmHandoverOnApprovedIsRejected() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.APPROVED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmHandover(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void confirmHandoverRequiresReservedUnit() {
        Transaction txn = pending(null);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmHandover(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void confirmHandoverOnNonReservedUnitIsRejected() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        unit.setStatus(AssetUnitStatus.BORROWED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmHandover(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void initiateReturnMovesActiveToReturnInitiated() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.ACTIVE);
        txn.setStartedAt(LocalDateTime.now());
        unit.setStatus(AssetUnitStatus.BORROWED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.initiateReturn(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.RETURN_INITIATED);
        assertThat(response.startedAt()).isNotNull();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.BORROWED);
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Return initiated"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.RETURN_INITIATED), eq(borrower), eq(null));
    }

    @Test
    void lenderCannotInitiateReturn() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.ACTIVE);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.initiateReturn(1L, owner))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void initiateReturnOnNonActiveIsRejected() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.initiateReturn(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reportHandbackMovesReturnInitiatedToReturnReported() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        txn.setStartedAt(LocalDateTime.now());
        unit.setStatus(AssetUnitStatus.BORROWED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(evidenceRepository.findByTransactionIdAndType(1L, EvidenceType.BORROWER_PRE_RETURN))
                .thenReturn(List.of(new Evidence()));
        when(evidenceRepository.findByTransactionIdAndType(1L, EvidenceType.BORROWER_RETURN_HANDOVER))
                .thenReturn(List.of(new Evidence()));

        TransactionResponse response = transactionService.reportHandback(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.RETURN_REPORTED);
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.reservationHeld()).isTrue();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.BORROWED);
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Handback reported"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.RETURN_REPORTED), eq(borrower), eq(null));
    }

    @Test
    void lenderCannotReportHandback() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.reportHandback(1L, owner))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void nonBorrowerCannotReportHandback() {
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(999L);
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.reportHandback(1L, intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void reportHandbackOnNonReturnInitiatedIsRejected() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.ACTIVE);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.reportHandback(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void confirmReturnCompletesLoanAndReleasesUnit() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_REPORTED);
        unit.setStatus(AssetUnitStatus.BORROWED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.confirmReturn(1L, owner);

        assertThat(response.state()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
        assertThat(response.reservationHeld()).isFalse();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.AVAILABLE);
        assertThat(txn.getReservedUnit()).isNull();
        verify(assetUnitRepository).save(unit);
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Loan completed"));
        verify(waitlistService).promoteForAsset(500L);
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.LOAN_COMPLETED), eq(owner), eq(null));
    }

    @Test
    void borrowerCannotConfirmReturnEvenAfterHandbackReported() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_REPORTED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmReturn(1L, borrower))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void lenderCannotPrematurelyCompleteBeforeHandbackReported() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmReturn(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void confirmReturnOnActiveIsRejected() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.ACTIVE);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmReturn(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── V2.2.4 loan accountability clock ────────────────────────────

    @Test
    void confirmHandoverStampsDueAtAndOriginalDueAt() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        txn.setAgreedDurationDays(7);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        // V2.5.1: lender must capture handover evidence before confirming.
        uploadLenderHandoverEvidence();

        TransactionResponse response = transactionService.confirmHandover(1L, owner);

        assertThat(response.dueAt()).isNotNull();
        assertThat(response.originalDueAt()).isNotNull();
        assertThat(response.dueAt()).isEqualTo(response.startedAt().plusDays(7));
        assertThat(response.originalDueAt()).isEqualTo(response.dueAt());
    }

    @Test
    void dueSoonIsTrueWithin24Hours() {
        Transaction txn = active(unit);
        txn.setStartedAt(LocalDateTime.now().minusHours(2));
        txn.setDueAt(LocalDateTime.now().plusHours(12));
        txn.setOriginalDueAt(txn.getDueAt());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        TransactionResponse response = transactionService.view(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.ACTIVE);
        assertThat(response.dueSoon()).isTrue();
        assertThat(response.overdue()).isFalse();
    }

    @Test
    void overdueIsTrueAfterDueAt() {
        Transaction txn = active(unit);
        txn.setStartedAt(LocalDateTime.now().minusDays(2));
        txn.setDueAt(LocalDateTime.now().minusHours(1));
        txn.setOriginalDueAt(txn.getDueAt());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        TransactionResponse response = transactionService.view(1L, borrower);

        assertThat(response.overdue()).isTrue();
        assertThat(response.dueSoon()).isFalse();
    }

    @Test
    void derivedTimingIsFalseOutsideActive() {
        Transaction txn = active(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        txn.setDueAt(LocalDateTime.now().minusHours(1));
        txn.setOriginalDueAt(txn.getDueAt());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        TransactionResponse response = transactionService.view(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.RETURN_INITIATED);
        assertThat(response.dueSoon()).isFalse();
        assertThat(response.overdue()).isFalse();
    }

    @Test
    void confirmReceiptAcceptsBorrowerInWindow() {
        Transaction txn = active(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.confirmReceipt(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.ACTIVE);
        assertThat(response.borrowerConfirmedAt()).isNotNull();
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Borrower confirmed receipt"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.HANDOVER_CONFIRMED), eq(borrower), eq(null));
    }

    @Test
    void confirmReceiptRejectsExpiredWindow() {
        Transaction txn = active(unit);
        txn.setStartedAt(LocalDateTime.now().minusMinutes(60));
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmReceipt(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("The handover confirmation window has closed");
    }

    @Test
    void confirmReceiptRejectsLender() {
        Transaction txn = active(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmReceipt(1L, owner))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void confirmReceiptRejectsDuplicateConfirmation() {
        Transaction txn = active(unit);
        txn.setBorrowerConfirmedAt(LocalDateTime.now());
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmReceipt(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Receipt has already been confirmed");
    }

    @Test
    void disputeHandoverAcceptsBorrowerInWindow() {
        Transaction txn = active(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.disputeHandover(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.HANDOVER_DISPUTED);
        assertThat(response.reservationHeld()).isFalse();
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Handover disputed"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.HANDOVER_DISPUTED), eq(borrower), eq(null));
    }

    @Test
    void disputeHandoverReleasesBorrowedUnit() {
        Transaction txn = active(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.disputeHandover(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.HANDOVER_DISPUTED);
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.AVAILABLE);
        assertThat(txn.getReservedUnit()).isNull();
        assertThat(txn.getReservedAt()).isNull();
        assertThat(response.reservationHeld()).isFalse();
        verify(assetUnitRepository).save(unit);
        verify(waitlistService).promoteForAsset(500L);
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.HANDOVER_DISPUTED), eq(borrower), eq(null));
    }

    @Test
    void disputeHandoverRejectsExpiredWindow() {
        Transaction txn = active(unit);
        txn.setStartedAt(LocalDateTime.now().minusMinutes(60));
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.disputeHandover(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("The handover confirmation window has closed");
    }

    @Test
    void disputeHandoverRejectsLender() {
        Transaction txn = active(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.disputeHandover(1L, owner))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void disputeHandoverRejectsNonActive() {
        Transaction txn = active(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.disputeHandover(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void handoverDisputedHasNoForwardTransitions() {
        Transaction txn = active(unit);
        txn.setState(TransactionStatus.HANDOVER_DISPUTED);
        unit.setStatus(AssetUnitStatus.BORROWED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.initiateReturn(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> transactionService.stageHandover(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> transactionService.confirmReceipt(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> transactionService.disputeHandover(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── agreed terms immutability ─────────────────────────────────────

    @Test
    void agreedTermsAreFrozenAfterApproval() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.APPROVED);
        txn.setAgreedPurpose("Football match practice");
        txn.setAgreedDurationDays(5);
        txn.setAgreedAt(LocalDateTime.of(2026, 1, 3, 9, 0));
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        // No state path mutates agreed fields; a second decision is rejected.
        assertThatThrownBy(() -> transactionService.approve(
                1L, new TransactionDecisionRequest("again"), owner))
                .isInstanceOf(BusinessRuleViolationException.class);

        txn.setAgreedPurpose("Different");
        assertThat(transactionService.view(1L, borrower).agreedPurpose()).isEqualTo("Different");
    }

    @Test
    void directApprovalSnapshotEqualsOriginalProposal() {
        Transaction txn = pending(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.approve(1L, null, owner);

        assertThat(response.agreedPurpose()).isEqualTo(txn.getPurpose());
        assertThat(response.agreedDurationDays()).isEqualTo(txn.getRequestedDurationDays());
    }

    // ── view / lists ──────────────────────────────────────────────────

    @Test
    void viewWorksForBorrowerAndLenderOnly() {
        Transaction txn = pending(unit);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        assertThat(transactionService.view(1L, borrower).id()).isEqualTo(1L);
        assertThat(transactionService.view(1L, owner).id()).isEqualTo(1L);
    }

    @Test
    void nonParticipantCannotView() {
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(999L);
        Transaction txn = pending(unit);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.view(1L, intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void missingTransactionForViewIsNotFound() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.view(1L, borrower))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listsAreScopedToBorrowerAndLender() {
        Transaction txn1 = pending(unit);
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(101L)).thenReturn(List.of(txn1));
        when(transactionRepository.findByLenderIdOrderByIdDesc(100L)).thenReturn(List.of(txn1));

        assertThat(transactionService.listForBorrower(borrower)).hasSize(1);
        assertThat(transactionService.listForLender(owner)).hasSize(1);
        verify(transactionRepository, never()).findAll();
    }

    // ── response hygiene ──────────────────────────────────────────────

    @Test
    void responseDoesNotExposeUnitIds() {
        when(listingRepository.findById(701L)).thenReturn(Optional.of(listing));
        stubActiveMember();
        stubAvailableUnit();
        stubSaveAndFlushReturnsArgument();

        TransactionResponse response = transactionService.create(
                new TransactionCreateRequest(701L, "Football match practice", 3), borrower);

        String json = response.toString();
        assertThat(json).doesNotContain("777");
        assertThat(json).doesNotContain("reservedUnitId");
        assertThat(json).doesNotContain("assetUnitId");
    }

    // ── V2.2.5 loan extensions ──────────────────────────────────────

    private Transaction activeWithDue(AssetUnit reserved) {
        Transaction txn = active(reserved);
        LocalDateTime due = txn.getStartedAt().plusDays(txn.getAgreedDurationDays());
        txn.setDueAt(due);
        txn.setOriginalDueAt(due);
        return txn;
    }

    private void stubMembership(long memberId) {
        lenient().when(membershipService.isActiveMember(eq(memberId), eq(900L))).thenReturn(true);
    }

    private void stubExtensionRepo(Transaction txn) {
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        stubMembership(101L);
        stubMembership(100L);
    }

    @Test
    void requestExtensionSetsFieldsAndEvent() {
        Transaction txn = activeWithDue(unit);
        stubExtensionRepo(txn);

        TransactionResponse response = transactionService.requestExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusDays(3), "Extra practice"), borrower);

        assertThat(response.extensionRequestedDueAt()).isEqualTo(txn.getDueAt().plusDays(3));
        assertThat(response.extensionOfferedDueAt()).isNull();
        assertThat(response.extensionNote()).isEqualTo("Extra practice");
        assertThat(response.extensionRequestedAt()).isNotNull();
        assertThat(response.extensionRequestPending()).isTrue();
        assertThat(response.extensionCounterPending()).isFalse();
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Extension requested"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.EXTENSION_REQUESTED), eq(borrower), anyString());
    }

    @Test
    void requestExtensionRejectsNotActive() {
        Transaction txn = activeWithDue(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.requestExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusDays(3), null), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Transaction is not in state ACTIVE");
    }

    @Test
    void requestExtensionRejectsLender() {
        Transaction txn = activeWithDue(unit);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.requestExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusDays(3), null), owner))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void requestExtensionRejectsNonParticipant() {
        Transaction txn = activeWithDue(unit);
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(999L);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.requestExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusDays(3), null), intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void requestExtensionRejectsNullDate() {
        Transaction txn = activeWithDue(unit);
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.requestExtension(
                1L, new ExtensionRequest(null, null), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("A new due date is required");
    }

    @Test
    void requestExtensionRejectsDateNotAfterDueAt() {
        Transaction txn = activeWithDue(unit);
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.requestExtension(
                1L, new ExtensionRequest(txn.getDueAt(), null), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("The new due date must be after the current due date");

        assertThatThrownBy(() -> transactionService.requestExtension(
                1L, new ExtensionRequest(txn.getDueAt().minusDays(1), null), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("The new due date must be after the current due date");
    }

    @Test
    void requestExtensionRejectsDateInPastOrPresent() {
        Transaction txn = activeWithDue(unit);
        txn.setDueAt(LocalDateTime.now().minusDays(1));
        txn.setOriginalDueAt(txn.getDueAt());
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.requestExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusHours(1), null), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("The new due date must be in the future");
    }

    @Test
    void requestExtensionRejectsDateBeyond30Days() {
        Transaction txn = activeWithDue(unit);
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.requestExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusDays(31), null), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("no more than");
    }

    @Test
    void requestExtensionRejectsDateAtExactly31Days() {
        Transaction txn = activeWithDue(unit);
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.requestExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusDays(30).plusMinutes(1), null), borrower))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void requestExtensionRejectsDuplicateWhilePending() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedAt(LocalDateTime.now());
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.requestExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusDays(3), null), borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("An extension request is already pending");
    }

    @Test
    void acceptExtensionUpdatesDueAtAndClears() {
        Transaction txn = activeWithDue(unit);
        LocalDateTime originalDue = txn.getOriginalDueAt();
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(3));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        stubExtensionRepo(txn);

        TransactionResponse response = transactionService.acceptExtension(1L, owner);

        assertThat(response.dueAt()).isEqualTo(originalDue.plusDays(3));
        assertThat(response.originalDueAt()).isEqualTo(originalDue);
        assertThat(response.extensionRequestedDueAt()).isNull();
        assertThat(response.extensionOfferedDueAt()).isNull();
        assertThat(response.extensionRequestedAt()).isNull();
        assertThat(response.extensionRequestPending()).isFalse();
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Extension approved"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.EXTENSION_APPROVED), eq(owner), eq(null));
    }

    @Test
    void acceptExtensionRejectsWhenNoRequestPending() {
        Transaction txn = activeWithDue(unit);
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.acceptExtension(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("No extension request is pending");
    }

    @Test
    void acceptExtensionRejectsWhenCounterPending() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(2));
        txn.setExtensionOfferedDueAt(txn.getDueAt().plusDays(1));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.acceptExtension(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("No extension request is pending");
    }

    @Test
    void acceptExtensionRejectsBorrower() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(3));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.acceptExtension(1L, borrower))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectExtensionLeavesDueAtAndClears() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(3));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        stubExtensionRepo(txn);

        TransactionResponse response = transactionService.rejectExtension(1L, owner);

        assertThat(response.dueAt()).isEqualTo(txn.getOriginalDueAt());
        assertThat(response.extensionRequestedDueAt()).isNull();
        assertThat(response.extensionRequestedAt()).isNull();
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Extension rejected"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.EXTENSION_REJECTED), eq(owner), eq(null));
    }

    @Test
    void rejectExtensionRejectsWhenNoRequestPending() {
        Transaction txn = activeWithDue(unit);
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.rejectExtension(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("No extension request is pending");
    }

    @Test
    void counterExtensionSetsOfferedAndEvent() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(5));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        stubExtensionRepo(txn);

        TransactionResponse response = transactionService.counterExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusDays(2), "Counter offer"), owner);

        assertThat(response.extensionOfferedDueAt()).isEqualTo(txn.getDueAt().plusDays(2));
        assertThat(response.extensionNote()).isEqualTo("Counter offer");
        assertThat(response.extensionRequestPending()).isFalse();
        assertThat(response.extensionCounterPending()).isTrue();
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Extension countered"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.EXTENSION_COUNTERED), eq(owner), anyString());
    }

    @Test
    void counterExtensionRejectsWhenNoRequestPending() {
        Transaction txn = activeWithDue(unit);
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.counterExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusDays(2), null), owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("No extension request is pending");
    }

    @Test
    void counterExtensionRejectsBorrower() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(5));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.counterExtension(
                1L, new ExtensionRequest(txn.getDueAt().plusDays(2), null), borrower))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void counterExtensionRejectsInvalidDate() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(5));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.counterExtension(
                1L, new ExtensionRequest(txn.getDueAt(), null), owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("The new due date must be after the current due date");
    }

    @Test
    void acceptExtensionCounterAppliesOfferedDate() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(5));
        txn.setExtensionOfferedDueAt(txn.getDueAt().plusDays(2));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        stubExtensionRepo(txn);

        TransactionResponse response = transactionService.acceptExtensionCounter(1L, borrower);

        assertThat(response.dueAt()).isEqualTo(txn.getOriginalDueAt().plusDays(2));
        assertThat(response.originalDueAt()).isEqualTo(txn.getOriginalDueAt());
        assertThat(response.extensionRequestedDueAt()).isNull();
        assertThat(response.extensionCounterPending()).isFalse();
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Extension counter accepted"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.EXTENSION_COUNTER_ACCEPTED), eq(borrower), eq(null));
    }

    @Test
    void acceptExtensionCounterRejectsNoCounterPending() {
        Transaction txn = activeWithDue(unit);
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.acceptExtensionCounter(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("No extension counter is pending");
    }

    @Test
    void acceptExtensionCounterRejectsLender() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(5));
        txn.setExtensionOfferedDueAt(txn.getDueAt().plusDays(2));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.acceptExtensionCounter(1L, owner))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectExtensionCounterLeavesDueAtAndClears() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(5));
        txn.setExtensionOfferedDueAt(txn.getDueAt().plusDays(2));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        stubExtensionRepo(txn);

        TransactionResponse response = transactionService.rejectExtensionCounter(1L, borrower);

        assertThat(response.dueAt()).isEqualTo(txn.getOriginalDueAt());
        assertThat(response.extensionRequestedAt()).isNull();
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Extension counter rejected"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.EXTENSION_COUNTER_REJECTED), eq(borrower), eq(null));
    }

    @Test
    void rejectExtensionCounterRejectsNoCounterPending() {
        Transaction txn = activeWithDue(unit);
        stubExtensionRepo(txn);

        assertThatThrownBy(() -> transactionService.rejectExtensionCounter(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("No extension counter is pending");
    }

    @Test
    void derivedExtensionFlagsFalseOutsideActive() {
        Transaction txn = activeWithDue(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(3));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        TransactionResponse response = transactionService.view(1L, borrower);

        assertThat(response.extensionRequestPending()).isFalse();
        assertThat(response.extensionCounterPending()).isFalse();
    }

    @Test
    void initiateReturnBlockedWhileExtensionPending() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(3));
        txn.setExtensionRequestedAt(LocalDateTime.now());
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.initiateReturn(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("An extension request is already pending");
    }

    @Test
    void disputeHandoverClearsExtensionNegotiation() {
        Transaction txn = activeWithDue(unit);
        txn.setExtensionRequestedDueAt(txn.getDueAt().plusDays(3));
        txn.setExtensionOfferedDueAt(txn.getDueAt().plusDays(1));
        txn.setExtensionNote("Counter");
        txn.setExtensionRequestedAt(LocalDateTime.now());
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        transactionService.disputeHandover(1L, borrower);

        verify(transactionRepository).save(argThat(tx -> tx.getExtensionRequestedAt() == null));
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Handover disputed"));
    }

    // ── V2.2.6 return disputes + evidence ────────────────────────────

    @Test
    void reportHandbackRejectedWithoutReturnEvidence() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.reportHandback(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Both return evidence photos are required");
    }

    @Test
    void reportHandbackRequiresBothEvidenceTypes() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(evidenceRepository.findByTransactionIdAndType(1L, EvidenceType.BORROWER_PRE_RETURN))
                .thenReturn(List.of(new Evidence()));

        assertThatThrownBy(() -> transactionService.reportHandback(1L, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Both return evidence photos are required");
    }

    @Test
    void disputeReturnMovesReportedToDisputedAndKeepsUnitBorrowed() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_REPORTED);
        unit.setStatus(AssetUnitStatus.BORROWED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);

        TransactionResponse response = transactionService.disputeReturn(1L, owner);

        assertThat(response.state()).isEqualTo(TransactionStatus.RETURN_DISPUTED);
        assertThat(response.returnDisputedAt()).isNotNull();
        assertThat(response.reservationHeld()).isTrue();
        assertThat(txn.getReturnDisputedBy().getId()).isEqualTo(100L);
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.BORROWED);
        verify(assetUnitRepository, never()).save(any(AssetUnit.class));
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Return disputed"));
        verify(eventService).createEventAndDeliveries(eq(txn), eq(TransactionEventType.RETURN_DISPUTED), eq(owner), eq(null));
    }

    @Test
    void borrowerCannotDisputeReturn() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_REPORTED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.disputeReturn(1L, borrower))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void disputeReturnOnNonReportedStateIsRejected() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(true);

        assertThatThrownBy(() -> transactionService.disputeReturn(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void disputeReturnRejectsInactiveLenderMember() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_REPORTED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(100L, 900L)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.disputeReturn(1L, owner))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void uploadEvidenceStoresPhotoAndReturnsResponse() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(evidenceStorageService.store(any(byte[].class))).thenReturn("11111111-2222-3333-4444-555555555555");
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> {
            Evidence e = inv.getArgument(0);
            e.setId(7L);
            return e;
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{1, 2, 3});

        EvidenceResponse response = transactionService.uploadEvidence(1L, EvidenceType.BORROWER_PRE_RETURN, file, null, null, borrower);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.type()).isEqualTo(EvidenceType.BORROWER_PRE_RETURN);
        assertThat(response.capturerId()).isEqualTo(101L);
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.sizeBytes()).isEqualTo(3L);
        assertThat(response.contentUrl()).endsWith("/evidence/7/content");
        assertThat(response.conditionNote()).isNull();
        assertThat(response.conditionRating()).isNull();
        verify(evidenceStorageService).store(any(byte[].class));
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Evidence added: BORROWER_PRE_RETURN"));
    }

    @Test
    void uploadEvidenceRejectsBorrowerUploadingLenderEvidence() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> transactionService.uploadEvidence(1L, EvidenceType.LENDER_HANDOVER, file, null, null, borrower))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void uploadEvidenceRejectsLender() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> transactionService.uploadEvidence(1L, EvidenceType.BORROWER_PRE_RETURN, file, null, null, owner))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void uploadEvidenceRejectsNonImageContentType() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", new byte[]{1});

        assertThatThrownBy(() -> transactionService.uploadEvidence(1L, EvidenceType.BORROWER_PRE_RETURN, file, null, null, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Evidence must be an image file");
    }

    @Test
    void uploadEvidenceRejectsEmptyFile() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> transactionService.uploadEvidence(1L, EvidenceType.BORROWER_PRE_RETURN, file, null, null, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("An evidence photo is required");
    }

    @Test
    void uploadEvidenceRejectsOversizedFile() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[5_242_881]);

        assertThatThrownBy(() -> transactionService.uploadEvidence(1L, EvidenceType.BORROWER_PRE_RETURN, file, null, null, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Evidence must be between 1 byte and");
    }

    @Test
    void uploadEvidenceRejectsAfterReturnReported() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_REPORTED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> transactionService.uploadEvidence(1L, EvidenceType.BORROWER_PRE_RETURN, file, null, null, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not in state");
    }

    // ── V2.5.1 borrow-side evidence + condition metadata ─────────────

    @Test
    void lenderPreLendingSucceedsInAwaitingHandoverWithConditionMetadata() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        AtomicReference<Evidence> saved = new AtomicReference<>();
        when(evidenceStorageService.store(any(byte[].class))).thenReturn("11111111-2222-3333-4444-555555555555");
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> {
            Evidence e = inv.getArgument(0);
            e.setId(7L);
            saved.set(e);
            return e;
        });

        EvidenceResponse response = transactionService.uploadEvidence(
                1L, EvidenceType.LENDER_PRE_LENDING, imagePhoto(), "Minor scratches", 4, owner);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.type()).isEqualTo(EvidenceType.LENDER_PRE_LENDING);
        assertThat(response.capturerId()).isEqualTo(100L);
        assertThat(response.conditionNote()).isEqualTo("Minor scratches");
        assertThat(response.conditionRating()).isEqualTo(4);
        assertThat(saved.get().getConditionNote()).isEqualTo("Minor scratches");
        assertThat(saved.get().getConditionRating()).isEqualTo(4);
        assertThat(saved.get().getCapturer().getId()).isEqualTo(100L);
        verify(evidenceStorageService).store(any(byte[].class));
    }

    @Test
    void lenderHandoverSucceedsInAwaitingHandoverWithConditionMetadata() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        AtomicReference<Evidence> saved = new AtomicReference<>();
        when(evidenceStorageService.store(any(byte[].class))).thenReturn("11111111-2222-3333-4444-555555555555");
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> {
            Evidence e = inv.getArgument(0);
            e.setId(8L);
            saved.set(e);
            return e;
        });

        EvidenceResponse response = transactionService.uploadEvidence(
                1L, EvidenceType.LENDER_HANDOVER, imagePhoto(), "Handed over in good order", 5, owner);

        assertThat(response.id()).isEqualTo(8L);
        assertThat(response.type()).isEqualTo(EvidenceType.LENDER_HANDOVER);
        assertThat(response.conditionNote()).isEqualTo("Handed over in good order");
        assertThat(response.conditionRating()).isEqualTo(5);
        assertThat(saved.get().getConditionNote()).isEqualTo("Handed over in good order");
        assertThat(saved.get().getConditionRating()).isEqualTo(5);
        assertThat(saved.get().getCapturer().getId()).isEqualTo(100L);
    }

    @Test
    void uploadEvidenceBorrowerReturnHandoverStillWorks() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(evidenceStorageService.store(any(byte[].class))).thenReturn("11111111-2222-3333-4444-555555555555");
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> {
            Evidence e = inv.getArgument(0);
            e.setId(9L);
            return e;
        });

        EvidenceResponse response = transactionService.uploadEvidence(
                1L, EvidenceType.BORROWER_RETURN_HANDOVER, imagePhoto(), null, null, borrower);

        assertThat(response.type()).isEqualTo(EvidenceType.BORROWER_RETURN_HANDOVER);
        assertThat(response.conditionNote()).isNull();
        assertThat(response.conditionRating()).isNull();
    }

    @Test
    void lenderPreLendingRejectedAfterActive() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.ACTIVE);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.uploadEvidence(
                1L, EvidenceType.LENDER_PRE_LENDING, imagePhoto(), null, null, owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not in state");
    }

    @Test
    void lenderHandoverRejectedAfterActive() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.ACTIVE);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.uploadEvidence(
                1L, EvidenceType.LENDER_HANDOVER, imagePhoto(), null, null, owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not in state");
    }

    @Test
    void lenderHandoverRejectedOutsideAwaitingHandover() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.APPROVED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.uploadEvidence(
                1L, EvidenceType.LENDER_HANDOVER, imagePhoto(), null, null, owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not in state");
    }

    @Test
    void uploadEvidenceRejectsNonParticipant() {
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(999L);
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.uploadEvidence(
                1L, EvidenceType.LENDER_PRE_LENDING, imagePhoto(), null, null, intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void uploadEvidenceAcceptsConditionRatingOne() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(evidenceStorageService.store(any(byte[].class))).thenReturn("11111111-2222-3333-4444-555555555555");
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> inv.getArgument(0));

        EvidenceResponse response = transactionService.uploadEvidence(
                1L, EvidenceType.BORROWER_PRE_RETURN, imagePhoto(), null, 1, borrower);

        assertThat(response.conditionRating()).isEqualTo(1);
    }

    @Test
    void uploadEvidenceAcceptsConditionRatingFive() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(evidenceStorageService.store(any(byte[].class))).thenReturn("11111111-2222-3333-4444-555555555555");
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> inv.getArgument(0));

        EvidenceResponse response = transactionService.uploadEvidence(
                1L, EvidenceType.BORROWER_PRE_RETURN, imagePhoto(), null, 5, borrower);

        assertThat(response.conditionRating()).isEqualTo(5);
    }

    @Test
    void uploadEvidenceRejectsConditionRatingZero() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.uploadEvidence(
                1L, EvidenceType.BORROWER_PRE_RETURN, imagePhoto(), null, 0, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Condition rating must be between 1 and 5");
    }

    @Test
    void uploadEvidenceRejectsConditionRatingSix() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.uploadEvidence(
                1L, EvidenceType.BORROWER_PRE_RETURN, imagePhoto(), null, 6, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Condition rating must be between 1 and 5");
    }

    @Test
    void uploadEvidenceRejectsNegativeConditionRating() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.uploadEvidence(
                1L, EvidenceType.BORROWER_PRE_RETURN, imagePhoto(), null, -1, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Condition rating must be between 1 and 5");
    }

    @Test
    void uploadEvidenceAcceptsOmittedConditionMetadata() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(evidenceStorageService.store(any(byte[].class))).thenReturn("11111111-2222-3333-4444-555555555555");
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> inv.getArgument(0));

        EvidenceResponse response = transactionService.uploadEvidence(
                1L, EvidenceType.BORROWER_PRE_RETURN, imagePhoto(), null, null, borrower);

        assertThat(response.conditionNote()).isNull();
        assertThat(response.conditionRating()).isNull();
    }

    @Test
    void uploadEvidenceRejectsConditionNoteOverMaxLength() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        String tooLong = "x".repeat(TransactionService.CONDITION_NOTE_MAX_LENGTH + 1);

        assertThatThrownBy(() -> transactionService.uploadEvidence(
                1L, EvidenceType.BORROWER_PRE_RETURN, imagePhoto(), tooLong, null, borrower))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Condition note must be at most " + TransactionService.CONDITION_NOTE_MAX_LENGTH + " characters");
    }

    @Test
    void confirmHandoverRejectedWithoutHandoverEvidence() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        txn.setAgreedDurationDays(3);
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.confirmHandover(1L, owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Handover evidence required before confirming handover");
    }

    @Test
    void listEvidenceVisibleToParticipants() {
        Transaction txn = pending(unit);
        txn.setState(TransactionStatus.RETURN_INITIATED);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));
        Evidence evidence = new Evidence();
        evidence.setId(9L);
        evidence.setTransaction(txn);
        evidence.setType(EvidenceType.BORROWER_PRE_RETURN);
        evidence.setCapturer(borrower);
        evidence.setContentType("image/png");
        evidence.setSizeBytes(3L);
        evidence.setCapturedAt(LocalDateTime.now());
        when(evidenceRepository.findByTransactionIdOrderByCapturedAtAsc(1L))
                .thenReturn(List.of(evidence));

        List<EvidenceResponse> result = transactionService.listEvidence(1L, borrower);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(9L);
        assertThat(result.get(0).capturerName()).isEqualTo("Salah");
    }

    @Test
    void listEvidenceRejectedForNonParticipant() {
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(999L);
        Transaction txn = pending(unit);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionService.listEvidence(1L, intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getEvidenceContentVisibleToParticipant() {
        Transaction txn = pending(unit);
        Evidence evidence = new Evidence();
        evidence.setId(9L);
        evidence.setTransaction(txn);
        evidence.setFileRef("11111111-2222-3333-4444-555555555555");
        evidence.setContentType("image/png");
        when(evidenceRepository.findById(9L)).thenReturn(Optional.of(evidence));
        when(evidenceStorageService.load("11111111-2222-3333-4444-555555555555"))
                .thenReturn(new byte[]{1, 2, 3});

        TransactionService.EvidenceContent content = transactionService.getEvidenceContent(9L, owner);

        assertThat(content.contentType()).isEqualTo("image/png");
        assertThat(content.bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void getEvidenceContentRejectedForNonParticipant() {
        User intruder = new User("Karim", "karim@example.com");
        intruder.setId(999L);
        Transaction txn = pending(unit);
        Evidence evidence = new Evidence();
        evidence.setId(9L);
        evidence.setTransaction(txn);
        evidence.setFileRef("11111111-2222-3333-4444-555555555555");
        when(evidenceRepository.findById(9L)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> transactionService.getEvidenceContent(9L, intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void missingEvidenceThrowsResourceNotFound() {
        assertThatThrownBy(() -> transactionService.getEvidenceContent(9L, borrower))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}