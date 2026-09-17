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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V2.2.7 queueing / waitlist.
 *
 * Locked behaviour:
 *  - The queue is per Asset and shared across every community that lists the
 *    asset; a borrower joins through one specific CommunityListing.
 *  - Joining is only allowed when availableUnits == 0. The join acquires the
 *    SAME pessimistic AssetUnit availability lock used by
 *    TransactionService.create() — availability is never a count-then-insert
 *    race.
 *  - UNIQUE(asset_id, borrower_id) is the database authority for one live
 *    position per Asset.
 *  - Promotion is synchronous inside the transaction that makes an AssetUnit
 *    AVAILABLE. Lock order is ALWAYS AssetUnit lock → waitlist-row lock →
 *    transaction work; the inverse order is never introduced.
 *  - Promotion revalidates the head waiter (listing listed, asset active,
 *    borrower still an active member of the queued community, borrower not the
 *    owner). An ineligible head is marked LEFT permanently and the loop moves
 *    to the next WAITING entry.
 *  - There is no scheduler and no @Async promotion.
 *  - Queue position is derived on read (countWaitingBefore + 1) and never
 *    persisted.
 *  - A voluntary leave hard-deletes the WAITING row (the borrower may re-join
 *    later). PROMOTED entries are ordinary transactions already and are not
 *    left through this service.
 */
@Service
public class WaitlistService {

    public static final int MAX_DURATION_DAYS = 30;

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final CommunityListingRepository listingRepository;
    private final AssetUnitRepository assetUnitRepository;
    private final TransactionRepository transactionRepository;
    private final MembershipService membershipService;
    private final TransactionMessageService messageService;

    public WaitlistService(WaitlistEntryRepository waitlistEntryRepository,
                           CommunityListingRepository listingRepository,
                           AssetUnitRepository assetUnitRepository,
                           TransactionRepository transactionRepository,
                           MembershipService membershipService,
                           TransactionMessageService messageService) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.listingRepository = listingRepository;
        this.assetUnitRepository = assetUnitRepository;
        this.transactionRepository = transactionRepository;
        this.membershipService = membershipService;
        this.messageService = messageService;
    }

    /**
     * Borrower joins the waitlist for the listed asset. Only allowed when no
     * AssetUnit is currently AVAILABLE.
     */
    @Transactional
    public WaitlistEntryResponse join(Long listingId, WaitlistJoinRequest request, User borrower) {
        requireUser(borrower);

        if (request == null || request.purpose() == null || request.purpose().isBlank()) {
            throw new BusinessRuleViolationException("Purpose is required");
        }
        if (request.requestedDurationDays() == null
                || request.requestedDurationDays() < 1
                || request.requestedDurationDays() > MAX_DURATION_DAYS) {
            throw new BusinessRuleViolationException(
                    "Duration must be between 1 and " + MAX_DURATION_DAYS + " days");
        }

        CommunityListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Listing not found with id: " + listingId));
        requireListed(listing);

        Asset asset = listing.getAsset();
        requireAssetActive(asset);

        Community community = listing.getCommunity();
        requireActiveMember(borrower.getId(), community.getId());

        if (asset.getOwner().getId().equals(borrower.getId())) {
            throw new BusinessRuleViolationException("You cannot request your own asset");
        }

        if (waitlistEntryRepository.existsByAssetIdAndBorrowerId(asset.getId(), borrower.getId())) {
            throw new BusinessRuleViolationException(
                    "You already have a waitlist position for this asset");
        }

        // Availability gate using the SAME pessimistic AssetUnit lock used by
        // TransactionService.create(): if a unit is AVAILABLE, the join is rejected
        // in favour of a normal request. This is not a count-then-insert race: the
        // locking read serialises against concurrent creates/promotions.
        if (assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(asset.getId()).isPresent()) {
            throw new BusinessRuleViolationException(
                    "An asset unit is currently available — submit a normal request instead");
        }

        WaitlistEntry entry = new WaitlistEntry();
        entry.setAsset(asset);
        entry.setListing(listing);
        entry.setBorrower(borrower);
        entry.setPurpose(request.purpose().trim());
        entry.setRequestedDurationDays(request.requestedDurationDays());
        entry.setStatus(WaitlistStatus.WAITING);
        try {
            WaitlistEntry saved = waitlistEntryRepository.saveAndFlush(entry);
            return toResponse(saved, positionOf(saved));
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(asset_id, borrower_id) is authoritative against raced duplicates.
            throw new BusinessRuleViolationException(
                    "You already have a waitlist position for this asset");
        }
    }

    /**
     * Borrower's live waitlist positions (WAITING only). Promoted entries have
     * become ordinary transactions and are shown there instead; LEFT entries are
     * permanently skipped and never revived.
     */
    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> listForBorrower(User borrower) {
        requireUser(borrower);
        return waitlistEntryRepository
                .findByBorrowerIdAndStatusOrderByCreatedAtAscIdAsc(borrower.getId(), WaitlistStatus.WAITING)
                .stream()
                .map(entry -> toResponse(entry, positionOf(entry)))
                .toList();
    }

    /**
     * Borrower leaves their own WAITING position. The row is HARD-DELETED so the
     * borrower may re-join the same asset later; no LEFT row is created for a
     * voluntary leave. After the delete, promotion runs in the same transaction
     * so an already-available unit can move to the next waiter.
     */
    @Transactional
    public WaitlistEntryResponse leave(Long entryId, User borrower) {
        requireUser(borrower);
        WaitlistEntry entry = waitlistEntryRepository.findByIdAndBorrowerId(entryId, borrower.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Waitlist entry not found with id: " + entryId));
        if (entry.getStatus() != WaitlistStatus.WAITING) {
            throw new BusinessRuleViolationException(
                    "Only a waiting position can be left; this entry is " + entry.getStatus());
        }

        Long assetId = entry.getAsset().getId();
        WaitlistEntryResponse response = toResponse(entry, 0);
        waitlistEntryRepository.delete(entry);
        promoteForAsset(assetId);
        return response;
    }

    /**
     * Critical V2.2.7 routine: synchronously promote waiters inside the SAME
     * transaction that made an AssetUnit AVAILABLE. Called from every unit →
     * AVAILABLE transition in TransactionService (reject, cancel, dispute
     * handover, confirm return) and after a voluntary leave.
     *
     * Lock order is always:
     *   1. AssetUnit availability lock (findFirstByAssetIdAndStatusForUpdate)
     *   2. waitlist head-row lock (findFirstWaitingForUpdate)
     *   3. transaction work
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void promoteForAsset(Long assetId) {
        while (true) {
            AssetUnit unit = assetUnitRepository.findFirstByAssetIdAndStatusForUpdate(assetId)
                    .orElse(null);
            if (unit == null) {
                return; // no AVAILABLE unit
            }

            WaitlistEntry entry = waitlistEntryRepository.findFirstWaitingForUpdate(assetId)
                    .orElse(null);
            if (entry == null) {
                return; // no waiting entry
            }

            if (!isEligible(entry)) {
                entry.setStatus(WaitlistStatus.LEFT);
                waitlistEntryRepository.save(entry);
                continue;
            }

            promote(entry, unit);
        }
    }

    private void promote(WaitlistEntry entry, AssetUnit unit) {
        Asset asset = entry.getAsset();
        User borrower = entry.getBorrower();
        CommunityListing listing = entry.getListing();
        Community community = listing.getCommunity();

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
        txn.setPurpose(entry.getPurpose());
        txn.setRequestedDurationDays(entry.getRequestedDurationDays());

        unit.setStatus(AssetUnitStatus.RESERVED);
        assetUnitRepository.save(unit);

        entry.setStatus(WaitlistStatus.PROMOTED);
        entry.setPromotedAt(now);
        waitlistEntryRepository.save(entry);

        try {
            // saveAndFlush surfaces UNIQUE(reserved_unit_id) violations
            // synchronously so a raced duplicate is a clean failure.
            transactionRepository.saveAndFlush(txn);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleViolationException("No available unit");
        }

        messageService.addSystemEvent(txn, "Promoted from waitlist");
    }

    private boolean isEligible(WaitlistEntry entry) {
        CommunityListing listing = entry.getListing();
        if (listing.getListingStatus() != ListingStatus.LISTED) {
            return false;
        }
        Asset asset = entry.getAsset();
        if (asset.getStatus() == AssetStatus.ARCHIVED) {
            return false;
        }
        User borrower = entry.getBorrower();
        if (asset.getOwner().getId().equals(borrower.getId())) {
            return false;
        }
        return membershipService.isActiveMember(borrower.getId(), listing.getCommunity().getId());
    }

    private long positionOf(WaitlistEntry entry) {
        return waitlistEntryRepository.countWaitingBefore(
                        entry.getAsset().getId(), entry.getCreatedAt(), entry.getId())
                + 1;
    }

    private WaitlistEntryResponse toResponse(WaitlistEntry entry, long position) {
        Asset asset = entry.getAsset();
        CommunityListing listing = entry.getListing();
        Community community = listing.getCommunity();
        return new WaitlistEntryResponse(
                entry.getId(),
                asset.getId(),
                asset.getTitle(),
                listing.getId(),
                community.getId(),
                community.getName(),
                entry.getBorrower().getId(),
                entry.getPurpose(),
                entry.getRequestedDurationDays(),
                entry.getStatus(),
                position,
                entry.getCreatedAt(),
                entry.getPromotedAt()
        );
    }

    private void requireUser(User actor) {
        if (actor == null) {
            throw new UnauthorizedException("Authentication is required");
        }
    }

    private void requireActiveMember(Long userId, Long communityId) {
        if (!membershipService.isActiveMember(userId, communityId)) {
            throw new UnauthorizedException(
                    "Only active members of this community can use its transactions");
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
}