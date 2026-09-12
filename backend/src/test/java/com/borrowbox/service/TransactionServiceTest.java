package com.borrowbox.service;

import com.borrowbox.dto.CounterOfferRequest;
import com.borrowbox.dto.TransactionCreateRequest;
import com.borrowbox.dto.TransactionDecisionRequest;
import com.borrowbox.dto.TransactionResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.ListingStatus;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CommunityListingRepository listingRepository;

    @Mock
    private AssetUnitRepository assetUnitRepository;

    @Mock
    private MembershipService membershipService;

    @Mock
    private TransactionMessageService messageService;

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
                membershipService, messageService);

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
        when(transactionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.confirmHandover(1L, owner);

        assertThat(response.state()).isEqualTo(TransactionStatus.ACTIVE);
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.reservationHeld()).isTrue();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.BORROWED);
        verify(assetUnitRepository).save(unit);
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Loan started"));
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

        TransactionResponse response = transactionService.reportHandback(1L, borrower);

        assertThat(response.state()).isEqualTo(TransactionStatus.RETURN_REPORTED);
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.reservationHeld()).isTrue();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.BORROWED);
        verify(messageService).addSystemEvent(any(Transaction.class), eq("Handback reported"));
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
}