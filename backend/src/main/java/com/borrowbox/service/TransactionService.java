package com.borrowbox.service;

import com.borrowbox.dto.CounterOfferRequest;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V2.2.1 transaction negotiation + V2.2.2 loan lifecycle + V2.2.3 system
 * timeline events + V2.2.4 loan accountability clock.
 *
 * Locked invariants (ADR-005 / V2.2.1):
 *  - Backend/database is authoritative for availability and reservation
 *    ordering. Client timestamps are never used to decide who wins.
 *  - A reservation is created under a PESSIMISTIC_WRITE lock on the AssetUnit;
 *    UNIQUE(reserved_unit_id) in the database is the authoritative backstop so
 *    exactly one open transaction may reference a given unit.
 *  - One AssetUnit may participate in at most one active (non-terminated)
 *    Transaction.
 *  - REJECTED / CANCELLED release the reservation immediately; APPROVED keeps it.
 *  - AssetUnit IDs are never exposed through responses.
 *  - V2.2.3: lifecycle transitions emit SYSTEM conversation events through
 *    TransactionMessageService inside the same backend transaction, so the
 *    state change and its timeline entry commit atomically.
 *
 * V2.2.4 loan accountability clock:
 *  - confirmHandover derives dueAt from the server clock:
 *    dueAt = startedAt + agreedDurationDays; originalDueAt is stamped equal to
 *    dueAt and never changes. Client timestamps never determine dueAt.
 *  - The 30-minute handover confirmation/dispute window opens at
 *    confirmHandover. The borrower may confirm receipt (persists
 *    borrowerConfirmedAt) or dispute non-receipt (moves the transaction to the
 *    terminal HANDOVER_DISPUTED state and releases the borrowed AssetUnit back
 *    to AVAILABLE) only while the window is open.
 *  - DUE_SOON (24h before dueAt) and OVERDUE are derived read-time conditions,
 *    never persisted states. No scheduler/background timer is introduced.
 *  - HANDOVER_DISPUTED has no forward transitions in V2.2.4.
 *
 * V2.2.5 loan extensions:
 *  - The borrower may request a new absolute due date while ACTIVE (including
 *    when currently OVERDUE). The lender may accept, reject, or counter, and
 *    the borrower must explicitly accept or reject a lender counter. The
 *    transaction stays ACTIVE throughout; no new TransactionStatus.
 *  - Exactly one pending extension negotiation exists at a time. It is
 *    persisted on the transaction (extensionRequestedDueAt /
 *    extensionOfferedDueAt / extensionNote / extensionRequestedAt) and
 *    represented to clients as derived read-time booleans
 *    (extensionRequestPending / extensionCounterPending), true only while
 *    ACTIVE.
 *  - dueAt changes only when an extension/counter is accepted; originalDueAt
 *    and agreedDurationDays never change. Accepting an extension while overdue
 *    immediately re-derives OVERDUE=false.
 *  - newDueAt must be present, strictly after the current dueAt, strictly after
 *    the server clock, and no more than EXTENSION_MAX_DAYS after the current
 *    dueAt.
 *  - Extensions never touch AssetUnit/reservation/availability state. The
 *    conversation timeline (SYSTEM events) is the durable negotiation history;
 *    there is no dedicated history table.
 *  - While an extension negotiation is pending, initiateReturn is rejected; a
 *    handover dispute clears the pending extension fields before recording
 *    HANDOVER_DISPUTED.
 */
@Service
public class TransactionService {

    public static final int MAX_DURATION_DAYS = 30;

    public static final long DUE_SOON_THRESHOLD_HOURS = 24;

    public static final long HANDOVER_WINDOW_MINUTES = 30;

    /**
     * V2.2.5: an extension/counter may move the due date no more than this many
     * days past the current due date.
     */
    public static final long EXTENSION_MAX_DAYS = 30;

    private final TransactionRepository transactionRepository;
    private final CommunityListingRepository listingRepository;
    private final AssetUnitRepository assetUnitRepository;
    private final MembershipService membershipService;
    private final TransactionMessageService messageService;

    public TransactionService(TransactionRepository transactionRepository,
                              CommunityListingRepository listingRepository,
                              AssetUnitRepository assetUnitRepository,
                              MembershipService membershipService,
                              TransactionMessageService messageService) {
        this.transactionRepository = transactionRepository;
        this.listingRepository = listingRepository;
        this.assetUnitRepository = assetUnitRepository;
        this.membershipService = membershipService;
        this.messageService = messageService;
    }

    /**
     * Borrower submits a structured request. Reserves one AVAILABLE unit of the
     * listed asset under a pessimistic write lock.
     */
    @Transactional
    public TransactionResponse create(TransactionCreateRequest request, User borrower) {
        requireUser(borrower);

        if (request == null) {
            throw new BusinessRuleViolationException("A request is required");
        }
        if (request.listingId() == null) {
            throw new BusinessRuleViolationException("A listing is required");
        }
        if (request.purpose() == null || request.purpose().isBlank()) {
            throw new BusinessRuleViolationException("Purpose is required");
        }
        if (request.requestedDurationDays() == null
                || request.requestedDurationDays() < 1
                || request.requestedDurationDays() > MAX_DURATION_DAYS) {
            throw new BusinessRuleViolationException(
                    "Duration must be between 1 and " + MAX_DURATION_DAYS + " days");
        }

        CommunityListing listing = listingRepository.findById(request.listingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Listing not found with id: " + request.listingId()));
        requireListed(listing);

        Asset asset = listing.getAsset();
        requireAssetActive(asset);

        Community community = listing.getCommunity();
        requireActiveMember(borrower.getId(), community.getId());

        if (asset.getOwner().getId().equals(borrower.getId())) {
            throw new BusinessRuleViolationException("You cannot request your own asset");
        }

        // Primary reservation guard: pick a unit under a PESSIMISTIC_WRITE lock.
        AssetUnit unit = assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(asset.getId())
                .orElseThrow(() -> new BusinessRuleViolationException("No available unit"));
        // The locking read sees the latest committed row: it can only be
        // AVAILABLE here, but the check keeps the invariant explicit.
        if (unit.getStatus() != AssetUnitStatus.AVAILABLE) {
            throw new BusinessRuleViolationException("No available unit");
        }
        unit.setStatus(AssetUnitStatus.RESERVED);

        LocalDateTime now = LocalDateTime.now();
        Transaction txn = new Transaction();
        txn.setCommunity(community);
        txn.setListing(listing);
        txn.setAsset(asset);
        txn.setBorrower(borrower);
        txn.setLender(asset.getOwner());
        txn.setReservedUnit(unit);
        txn.setReservedAt(now);
        txn.setState(TransactionStatus.PENDING);
        txn.setPurpose(request.purpose().trim());
        txn.setRequestedDurationDays(request.requestedDurationDays());

        try {
            assetUnitRepository.save(unit);
            // saveAndFlush surfaces UNIQUE(reserved_unit_id) violations
            // synchronously so a raced duplicate becomes a clean 400 instead of
            // a deferred rollback-only failure.
            return toResponse(transactionRepository.saveAndFlush(txn));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleViolationException("No available unit");
        }
    }

    /**
     * Lender approves a PENDING request and writes the agreed-terms snapshot
     * from the originally proposed purpose/duration. The reservation is kept.
     */
    @Transactional
    public TransactionResponse approve(Long id, TransactionDecisionRequest request, User lender) {
        requireUser(lender);
        Transaction txn = findForUpdate(id);
        requireLender(txn, lender);
        requireActiveMember(lender.getId(), txn.getCommunity().getId());
        requireListed(txn.getListing());
        requireState(txn, TransactionStatus.PENDING);

        LocalDateTime now = LocalDateTime.now();
        txn.setState(TransactionStatus.APPROVED);
        txn.setAgreedPurpose(txn.getPurpose());
        txn.setAgreedDurationDays(txn.getRequestedDurationDays());
        txn.setAgreedAt(now);
        txn.setDecidedAt(now);
        txn.setDecidedBy(lender);
        txn.setDecisionNote(note(request));
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * Lender rejects a PENDING request. The reservation is released immediately.
     */
    @Transactional
    public TransactionResponse reject(Long id, TransactionDecisionRequest request, User lender) {
        requireUser(lender);
        Transaction txn = findForUpdate(id);
        requireLender(txn, lender);
        requireActiveMember(lender.getId(), txn.getCommunity().getId());
        requireListed(txn.getListing());
        requireState(txn, TransactionStatus.PENDING);

        LocalDateTime now = LocalDateTime.now();
        txn.setState(TransactionStatus.REJECTED);
        txn.setDecidedAt(now);
        txn.setDecidedBy(lender);
        txn.setDecisionNote(note(request));
        releaseReservation(txn);
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * Lender answers a PENDING request with modified terms. The borrower must
     * then accept or cancel; there is no second lender round in V2.2.1.
     */
    @Transactional
    public TransactionResponse counterOffer(Long id, CounterOfferRequest request, User lender) {
        requireUser(lender);
        if (request == null || request.requestedDurationDays() == null
                || request.requestedDurationDays() < 1
                || request.requestedDurationDays() > MAX_DURATION_DAYS) {
            throw new BusinessRuleViolationException(
                    "Counter-offer duration must be between 1 and " + MAX_DURATION_DAYS + " days");
        }

        Transaction txn = findForUpdate(id);
        requireLender(txn, lender);
        requireActiveMember(lender.getId(), txn.getCommunity().getId());
        requireListed(txn.getListing());
        requireState(txn, TransactionStatus.PENDING);

        LocalDateTime now = LocalDateTime.now();
        String counterPurpose = (request.purpose() == null || request.purpose().isBlank())
                ? txn.getPurpose()
                : request.purpose().trim();
        txn.setCounterPurpose(counterPurpose);
        txn.setCounterDurationDays(request.requestedDurationDays());
        txn.setCounterNote(trimToNull(request.note()));
        txn.setCounterOfferedAt(now);
        txn.setState(TransactionStatus.COUNTER_OFFERED);
        txn.setDecidedAt(now);
        txn.setDecidedBy(lender);
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * Borrower accepts a counter-offer. The agreed-terms snapshot is written
     * from the counter terms and the reservation is kept.
     */
    @Transactional
    public TransactionResponse acceptCounter(Long id, User borrower) {
        requireUser(borrower);
        Transaction txn = findForUpdate(id);
        requireBorrower(txn, borrower);
        requireState(txn, TransactionStatus.COUNTER_OFFERED);

        LocalDateTime now = LocalDateTime.now();
        txn.setState(TransactionStatus.APPROVED);
        txn.setAgreedPurpose(txn.getCounterPurpose());
        txn.setAgreedDurationDays(txn.getCounterDurationDays());
        txn.setAgreedAt(now);
        txn.setDecidedAt(now);
        txn.setDecidedBy(borrower);
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * Cancellation rules:
     *  - borrower may cancel PENDING / COUNTER_OFFERED
     *  - either party may cancel AWAITING_HANDOVER (absorbs the removed
     *    V2.2.1 APPROVED escape hatch; APPROVED itself is forward-only)
     * The reservation is released immediately.
     */
    @Transactional
    public TransactionResponse cancel(Long id, User actor) {
        requireUser(actor);
        Transaction txn = findForUpdate(id);

        boolean isBorrower = txn.getBorrower().getId().equals(actor.getId());
        boolean isLender = txn.getLender().getId().equals(actor.getId());
        if (!isBorrower && !isLender) {
            throw new UnauthorizedException("Only the borrower or lender can cancel a transaction");
        }

        TransactionStatus state = txn.getState();
        boolean allowed = switch (state) {
            case PENDING, COUNTER_OFFERED -> isBorrower;
            case AWAITING_HANDOVER -> isBorrower || isLender;
            default -> false;
        };
        if (!allowed) {
            throw new BusinessRuleViolationException(
                    "A transaction in state " + state + " cannot be cancelled by this user");
        }

        txn.setState(TransactionStatus.CANCELLED);
        releaseReservation(txn);
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * Either party stages an APPROVED transaction into AWAITING_HANDOVER.
     * The reservation is kept (unit stays RESERVED).
     */
    @Transactional
    public TransactionResponse stageHandover(Long id, User actor) {
        requireUser(actor);
        Transaction txn = findForUpdate(id);
        requireParticipant(txn, actor);
        requireState(txn, TransactionStatus.APPROVED);

        txn.setState(TransactionStatus.AWAITING_HANDOVER);
        messageService.addSystemEvent(txn, "Handover scheduled");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * Lender confirms the physical handover. The loan clock starts here
     * (startedAt, backend-authoritative) and the reserved unit flips
     * RESERVED → BORROWED, moving the transaction to ACTIVE.
     */
    @Transactional
    public TransactionResponse confirmHandover(Long id, User lender) {
        requireUser(lender);
        Transaction txn = findForUpdate(id);
        requireLender(txn, lender);
        requireState(txn, TransactionStatus.AWAITING_HANDOVER);

        AssetUnit unit = txn.getReservedUnit();
        if (unit == null || unit.getStatus() != AssetUnitStatus.RESERVED) {
            throw new BusinessRuleViolationException("The reserved unit is not available to hand over");
        }

        LocalDateTime now = LocalDateTime.now();
        txn.setState(TransactionStatus.ACTIVE);
        txn.setStartedAt(now);
        // V2.2.4: authoritative loan clock. dueAt derives from the server clock
        // and the agreed duration; originalDueAt is captured once and never
        // changes (future extensions may modify dueAt only).
        LocalDateTime due = now.plusDays(txn.getAgreedDurationDays());
        txn.setDueAt(due);
        txn.setOriginalDueAt(due);
        unit.setStatus(AssetUnitStatus.BORROWED);
        assetUnitRepository.save(unit);
        messageService.addSystemEvent(txn, "Loan started");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * V2.2.4: borrower explicitly confirms receipt of the item within the
     * 30-minute handover confirmation/dispute window. The transaction stays
     * ACTIVE; borrowerConfirmedAt is persisted by the server clock. A second
     * confirmation is rejected.
     */
    @Transactional
    public TransactionResponse confirmReceipt(Long id, User borrower) {
        requireUser(borrower);
        Transaction txn = findForUpdate(id);
        requireBorrower(txn, borrower);
        requireState(txn, TransactionStatus.ACTIVE);
        requireHandoverWindowOpen(txn);

        if (txn.getBorrowerConfirmedAt() != null) {
            throw new BusinessRuleViolationException("Receipt has already been confirmed");
        }

        txn.setBorrowerConfirmedAt(LocalDateTime.now());
        messageService.addSystemEvent(txn, "Borrower confirmed receipt");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * V2.2.4: borrower disputes non-receipt within the 30-minute handover
     * confirmation/dispute window. The borrowed AssetUnit flips BORROWED →
     * AVAILABLE immediately, the reservation handle is released and the
     * transaction moves to the terminal HANDOVER_DISPUTED state. HANDOVER_DISPUTED
     * has no forward transitions in V2.2.4; resolution is later-stage scope.
     *
     * V2.2.5: any pending extension negotiation is cleared so the terminal
     * record never carries orphaned extension fields.
     */
    @Transactional
    public TransactionResponse disputeHandover(Long id, User borrower) {
        requireUser(borrower);
        Transaction txn = findForUpdate(id);
        requireBorrower(txn, borrower);
        requireState(txn, TransactionStatus.ACTIVE);
        requireHandoverWindowOpen(txn);

        releaseBorrowedUnit(txn);
        clearExtensionNegotiation(txn);
        txn.setState(TransactionStatus.HANDOVER_DISPUTED);
        messageService.addSystemEvent(txn, "Handover disputed");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * V2.2.5: borrower requests a new absolute due date while ACTIVE (including
     * when currently OVERDUE). Rejected when any extension negotiation is
     * already pending. The transaction stays ACTIVE; the request is persisted
     * on the transaction and the SYSTEM event is the durable history record.
     */
    @Transactional
    public TransactionResponse requestExtension(Long id, ExtensionRequest request, User borrower) {
        requireUser(borrower);
        Transaction txn = findForUpdate(id);
        requireBorrower(txn, borrower);
        requireActiveMember(borrower.getId(), txn.getCommunity().getId());
        requireState(txn, TransactionStatus.ACTIVE);
        validExtensionRequest(request);
        validateExtensionDate(txn, request.newDueAt());
        requireNoPendingExtension(txn);

        LocalDateTime now = LocalDateTime.now();
        txn.setExtensionRequestedDueAt(request.newDueAt());
        txn.setExtensionOfferedDueAt(null);
        txn.setExtensionNote(trimToNull(request.note()));
        txn.setExtensionRequestedAt(now);
        messageService.addSystemEvent(txn, "Extension requested");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * V2.2.5: lender accepts the pending borrower request. dueAt becomes the
     * requested date; originalDueAt and agreedDurationDays are preserved.
     */
    @Transactional
    public TransactionResponse acceptExtension(Long id, User lender) {
        requireUser(lender);
        Transaction txn = findForUpdate(id);
        requireLender(txn, lender);
        requireActiveMember(lender.getId(), txn.getCommunity().getId());
        requireState(txn, TransactionStatus.ACTIVE);
        requirePendingExtensionRequest(txn);

        txn.setDueAt(txn.getExtensionRequestedDueAt());
        clearExtensionNegotiation(txn);
        messageService.addSystemEvent(txn, "Extension approved");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * V2.2.5: lender rejects the pending borrower request. dueAt is unchanged.
     */
    @Transactional
    public TransactionResponse rejectExtension(Long id, User lender) {
        requireUser(lender);
        Transaction txn = findForUpdate(id);
        requireLender(txn, lender);
        requireActiveMember(lender.getId(), txn.getCommunity().getId());
        requireState(txn, TransactionStatus.ACTIVE);
        requirePendingExtensionRequest(txn);

        clearExtensionNegotiation(txn);
        messageService.addSystemEvent(txn, "Extension rejected");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * V2.2.5: lender answers the pending borrower request with a counter offer.
     * The countered date is validated against the current dueAt (and the server
     * clock); a counter that is already outstanding cannot be re-countered.
     * The counter is not final until the borrower accepts it explicitly.
     */
    @Transactional
    public TransactionResponse counterExtension(Long id, ExtensionRequest request, User lender) {
        requireUser(lender);
        Transaction txn = findForUpdate(id);
        requireLender(txn, lender);
        requireActiveMember(lender.getId(), txn.getCommunity().getId());
        requireState(txn, TransactionStatus.ACTIVE);
        validExtensionRequest(request);
        validateExtensionDate(txn, request.newDueAt());
        requirePendingExtensionRequest(txn);

        txn.setExtensionOfferedDueAt(request.newDueAt());
        txn.setExtensionNote(trimToNull(request.note()));
        messageService.addSystemEvent(txn, "Extension countered");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * V2.2.5: borrower accepts the lender counter. dueAt becomes the countered
     * date; originalDueAt and agreedDurationDays are preserved.
     */
    @Transactional
    public TransactionResponse acceptExtensionCounter(Long id, User borrower) {
        requireUser(borrower);
        Transaction txn = findForUpdate(id);
        requireBorrower(txn, borrower);
        requireActiveMember(borrower.getId(), txn.getCommunity().getId());
        requireState(txn, TransactionStatus.ACTIVE);
        requirePendingExtensionCounter(txn);

        txn.setDueAt(txn.getExtensionOfferedDueAt());
        clearExtensionNegotiation(txn);
        messageService.addSystemEvent(txn, "Extension counter accepted");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * V2.2.5: borrower rejects the lender counter. dueAt is unchanged.
     */
    @Transactional
    public TransactionResponse rejectExtensionCounter(Long id, User borrower) {
        requireUser(borrower);
        Transaction txn = findForUpdate(id);
        requireBorrower(txn, borrower);
        requireActiveMember(borrower.getId(), txn.getCommunity().getId());
        requireState(txn, TransactionStatus.ACTIVE);
        requirePendingExtensionCounter(txn);

        clearExtensionNegotiation(txn);
        messageService.addSystemEvent(txn, "Extension counter rejected");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * Borrower initiates the return of an ACTIVE loan. The unit stays BORROWED;
     * the borrower is starting the return process and coordinating the physical
     * handback through the conversation.
     *
     * V2.2.5: a pending extension negotiation must be resolved before the
     * return flow can start.
     */
    @Transactional
    public TransactionResponse initiateReturn(Long id, User borrower) {
        requireUser(borrower);
        Transaction txn = findForUpdate(id);
        requireBorrower(txn, borrower);
        requireState(txn, TransactionStatus.ACTIVE);
        requireNoPendingExtension(txn);

        txn.setState(TransactionStatus.RETURN_INITIATED);
        messageService.addSystemEvent(txn, "Return initiated");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * Borrower reports that the item has been physically handed back while a
     * return is in progress. The unit stays BORROWED until a lender receipt;
     * the transition is borrower-only and guarded against invalid states.
     */
    @Transactional
    public TransactionResponse reportHandback(Long id, User borrower) {
        requireUser(borrower);
        Transaction txn = findForUpdate(id);
        requireBorrower(txn, borrower);
        requireState(txn, TransactionStatus.RETURN_INITIATED);

        txn.setState(TransactionStatus.RETURN_REPORTED);
        messageService.addSystemEvent(txn, "Handback reported");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * Lender confirms receipt of the returned unit. This closes the loan:
     * completedAt records the backend clock, the unit flips BORROWED →
     * AVAILABLE and the reservation handle is released. Only a RETURN_REPORTED
     * transaction may be completed: the lender cannot finish a loan before the
     * borrower has reported the physical handback.
     */
    @Transactional
    public TransactionResponse confirmReturn(Long id, User lender) {
        requireUser(lender);
        Transaction txn = findForUpdate(id);
        requireLender(txn, lender);
        requireState(txn, TransactionStatus.RETURN_REPORTED);

        AssetUnit unit = txn.getReservedUnit();
        if (unit == null) {
            throw new BusinessRuleViolationException("The returned unit is not attached to this transaction");
        }

        txn.setState(TransactionStatus.COMPLETED);
        txn.setCompletedAt(LocalDateTime.now());
        unit.setStatus(AssetUnitStatus.AVAILABLE);
        assetUnitRepository.save(unit);

        // Release the reservation handle so no other transaction claims this unit.
        txn.setReservedUnit(null);
        txn.setReservedAt(null);
        messageService.addSystemEvent(txn, "Loan completed");
        return toResponse(transactionRepository.save(txn));
    }

    @Transactional(readOnly = true)
    public TransactionResponse view(Long id, User actor) {
        requireUser(actor);
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        requireParticipant(txn, actor);
        return toResponse(txn);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listForBorrower(User borrower) {
        requireUser(borrower);
        return transactionRepository.findByBorrowerIdOrderByIdDesc(borrower.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listForLender(User lender) {
        requireUser(lender);
        return transactionRepository.findByLenderIdOrderByIdDesc(lender.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private void releaseReservation(Transaction txn) {
        AssetUnit unit = txn.getReservedUnit();
        if (unit != null) {
            if (unit.getStatus() == AssetUnitStatus.RESERVED) {
                unit.setStatus(AssetUnitStatus.AVAILABLE);
                assetUnitRepository.save(unit);
            }
            txn.setReservedUnit(null);
            txn.setReservedAt(null);
        }
    }

    /**
     * V2.2.4: releases a currently BORROWED AssetUnit back to AVAILABLE and
     * clears the reservation handle, following the same pattern as
     * releaseReservation but for the loan-until-dispute case.
     */
    private void releaseBorrowedUnit(Transaction txn) {
        AssetUnit unit = txn.getReservedUnit();
        if (unit != null) {
            if (unit.getStatus() == AssetUnitStatus.BORROWED) {
                unit.setStatus(AssetUnitStatus.AVAILABLE);
                assetUnitRepository.save(unit);
            }
            txn.setReservedUnit(null);
            txn.setReservedAt(null);
        }
    }

    private Transaction findForUpdate(Long id) {
        return transactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
    }

    private void requireUser(User actor) {
        if (actor == null) {
            throw new UnauthorizedException("Authentication is required");
        }
    }

    private void requireLender(Transaction txn, User actor) {
        if (!txn.getLender().getId().equals(actor.getId())) {
            throw new UnauthorizedException("Only the asset owner can decide on this transaction");
        }
    }

    private void requireBorrower(Transaction txn, User actor) {
        if (!txn.getBorrower().getId().equals(actor.getId())) {
            throw new UnauthorizedException("Only the borrower can act on this transaction");
        }
    }

    private void requireParticipant(Transaction txn, User actor) {
        if (!txn.getBorrower().getId().equals(actor.getId())
                && !txn.getLender().getId().equals(actor.getId())) {
            throw new UnauthorizedException("Only participants can view this transaction");
        }
    }

    private void requireActiveMember(Long userId, Long communityId) {
        if (!membershipService.isActiveMember(userId, communityId)) {
            throw new UnauthorizedException(
                    "Only active members of this community can use its transactions");
        }
    }

    private void requireState(Transaction txn, TransactionStatus expected) {
        if (txn.getState() != expected) {
            throw new BusinessRuleViolationException(
                    "Transaction is not in state " + expected);
        }
    }

    /**
     * V2.2.5: verifies the extension/counter negotiation input is present and
     * dates valid as a request made against the current dueAt.
     */
    private void validExtensionRequest(ExtensionRequest request) {
        if (request == null || request.newDueAt() == null) {
            throw new BusinessRuleViolationException("A new due date is required");
        }
    }

    /**
     * V2.2.5: a new due date must be strictly after the current dueAt, strictly
     * after the server clock, and no more than EXTENSION_MAX_DAYS after the
     * current dueAt.
     */
    private void validateExtensionDate(Transaction txn, LocalDateTime newDueAt) {
        LocalDateTime currentDueAt = txn.getDueAt();
        if (currentDueAt == null) {
            throw new BusinessRuleViolationException("Transaction has no due date");
        }
        if (!newDueAt.isAfter(currentDueAt)) {
            throw new BusinessRuleViolationException(
                    "The new due date must be after the current due date");
        }
        if (!newDueAt.isAfter(LocalDateTime.now())) {
            throw new BusinessRuleViolationException(
                    "The new due date must be in the future");
        }
        if (newDueAt.isAfter(currentDueAt.plusDays(EXTENSION_MAX_DAYS))) {
            throw new BusinessRuleViolationException(
                    "The new due date must be no more than " + EXTENSION_MAX_DAYS
                            + " days after the current due date");
        }
    }

    /**
     * V2.2.5: exactly one extension negotiation may be pending at a time.
     */
    private void requireNoPendingExtension(Transaction txn) {
        if (hasPendingExtension(txn)) {
            throw new BusinessRuleViolationException("An extension request is already pending");
        }
    }

    private void requirePendingExtensionRequest(Transaction txn) {
        if (!hasPendingExtensionRequest(txn)) {
            throw new BusinessRuleViolationException("No extension request is pending");
        }
    }

    private void requirePendingExtensionCounter(Transaction txn) {
        if (!hasPendingExtensionCounter(txn)) {
            throw new BusinessRuleViolationException("No extension counter is pending");
        }
    }

    private boolean hasPendingExtension(Transaction txn) {
        return txn.getExtensionRequestedAt() != null;
    }

    private boolean hasPendingExtensionRequest(Transaction txn) {
        return hasPendingExtension(txn) && txn.getExtensionOfferedDueAt() == null;
    }

    private boolean hasPendingExtensionCounter(Transaction txn) {
        return hasPendingExtension(txn) && txn.getExtensionOfferedDueAt() != null;
    }

    /**
     * V2.2.5 derived read-time conditions. Never persisted as states; true only
     * while the transaction is ACTIVE so RETURN_INITIATED / RETURN_REPORTED and
     * terminal rows never report a pending negotiation.
     */
    private boolean isExtensionRequestPending(Transaction txn) {
        return txn.getState() == TransactionStatus.ACTIVE
                && hasPendingExtension(txn)
                && txn.getExtensionOfferedDueAt() == null;
    }

    private boolean isExtensionCounterPending(Transaction txn) {
        return txn.getState() == TransactionStatus.ACTIVE
                && hasPendingExtension(txn)
                && txn.getExtensionOfferedDueAt() != null;
    }

    private void clearExtensionNegotiation(Transaction txn) {
        txn.setExtensionRequestedDueAt(null);
        txn.setExtensionOfferedDueAt(null);
        txn.setExtensionNote(null);
        txn.setExtensionRequestedAt(null);
    }

    /**
     * V2.2.4: verifies the borrower handover confirmation/dispute window is
     * still open. The window derives from the server-stamped handover time and
     * is never extended by client input. The caller must already have verified
     * the ACTIVE state.
     */
    private void requireHandoverWindowOpen(Transaction txn) {
        LocalDateTime startedAt = txn.getStartedAt();
        if (startedAt == null || !LocalDateTime.now().isBefore(startedAt.plusMinutes(HANDOVER_WINDOW_MINUTES))) {
            throw new BusinessRuleViolationException("The handover confirmation window has closed");
        }
    }

    /**
     * V2.2.4 derived read-time conditions. NEVER persisted as states.
     */
    private boolean isDueSoon(Transaction txn) {
        if (txn.getState() != TransactionStatus.ACTIVE || txn.getDueAt() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = txn.getDueAt().minusHours(DUE_SOON_THRESHOLD_HOURS);
        return !now.isBefore(threshold) && now.isBefore(txn.getDueAt());
    }

    private boolean isOverdue(Transaction txn) {
        if (txn.getState() != TransactionStatus.ACTIVE || txn.getDueAt() == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(txn.getDueAt());
    }

    private boolean isHandoverWindowOpen(Transaction txn) {
        if (txn.getState() != TransactionStatus.ACTIVE || txn.getStartedAt() == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(txn.getStartedAt().plusMinutes(HANDOVER_WINDOW_MINUTES));
    }

    private void requireListed(CommunityListing listing) {
        if (listing.getListingStatus() != ListingStatus.LISTED) {
            throw new BusinessRuleViolationException(
                    "This asset is not currently listed in this community");
        }
    }

    private void requireAssetActive(Asset asset) {
        if (asset.getStatus() == AssetStatus.ARCHIVED) {
            throw new BusinessRuleViolationException("An archived asset cannot be requested");
        }
    }

    private String note(TransactionDecisionRequest request) {
        if (request == null) {
            return null;
        }
        return trimToNull(request.note());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private TransactionResponse toResponse(Transaction txn) {
        Asset asset = txn.getAsset();
        Community community = txn.getCommunity();
        User borrower = txn.getBorrower();
        User lender = txn.getLender();
        return new TransactionResponse(
                txn.getId(),
                community.getId(),
                community.getName(),
                txn.getListing().getId(),
                asset.getId(),
                asset.getTitle(),
                lender.getId(),
                lender.getFullName(),
                borrower.getId(),
                borrower.getFullName(),
                txn.getState(),
                txn.getPurpose(),
                txn.getRequestedDurationDays(),
                txn.getCounterPurpose(),
                txn.getCounterDurationDays(),
                txn.getCounterNote(),
                txn.getCounterOfferedAt(),
                txn.getAgreedPurpose(),
                txn.getAgreedDurationDays(),
                txn.getAgreedAt(),
                txn.getDecisionNote(),
                txn.getReservedUnit() != null,
                txn.getStartedAt(),
                txn.getDueAt(),
                txn.getOriginalDueAt(),
                txn.getBorrowerConfirmedAt(),
                txn.getExtensionRequestedDueAt(),
                txn.getExtensionOfferedDueAt(),
                txn.getExtensionNote(),
                txn.getExtensionRequestedAt(),
                isExtensionRequestPending(txn),
                isExtensionCounterPending(txn),
                isDueSoon(txn),
                isOverdue(txn),
                isHandoverWindowOpen(txn),
                txn.getCompletedAt(),
                txn.getCreatedAt(),
                txn.getUpdatedAt()
        );
    }
}