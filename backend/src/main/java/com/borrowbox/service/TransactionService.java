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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V2.2.1 transaction negotiation + V2.2.2 loan lifecycle + V2.2.3 system
 * timeline events.
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
 */
@Service
public class TransactionService {

    public static final int MAX_DURATION_DAYS = 30;

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

        txn.setState(TransactionStatus.ACTIVE);
        txn.setStartedAt(LocalDateTime.now());
        unit.setStatus(AssetUnitStatus.BORROWED);
        assetUnitRepository.save(unit);
        messageService.addSystemEvent(txn, "Loan started");
        return toResponse(transactionRepository.save(txn));
    }

    /**
     * Borrower initiates the return of an ACTIVE loan. The unit stays BORROWED;
     * the borrower is starting the return process and coordinating the physical
     * handback through the conversation.
     */
    @Transactional
    public TransactionResponse initiateReturn(Long id, User borrower) {
        requireUser(borrower);
        Transaction txn = findForUpdate(id);
        requireBorrower(txn, borrower);
        requireState(txn, TransactionStatus.ACTIVE);

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
                txn.getCompletedAt(),
                txn.getCreatedAt(),
                txn.getUpdatedAt()
        );
    }
}