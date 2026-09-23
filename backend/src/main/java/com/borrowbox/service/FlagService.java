package com.borrowbox.service;

import com.borrowbox.entity.Community;
import com.borrowbox.entity.Flag;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.FlagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V2.4.1 Community Health + Moderation: flag workflow foundation.
 *
 * Flags are community-scoped moderation records. Every mutation and query is
 * gated behind an active community manager (the acting manager is the
 * authorizer; the reporter of a flag is a separate, always-optional attribution
 * field that stays immutable after creation). Core incident facts
 * (community, transaction, flagType, reporter, occurredAt) are stamped at
 * creation and are never exposed through any mutation API; only the workflow
 * fields status / assignee / note may change.
 *
 * In this foundation slice no incident is flagged automatically and nothing
 * calls back into the loan lifecycle: creating a flag never changes a
 * Transaction or an AssetUnit, and no automatic Flag is created when a loan
 * goes overdue.
 */
@Service
@Transactional(readOnly = true)
public class FlagService {

    public static final int MAX_NOTE_LENGTH = 2000;

    private final FlagRepository flagRepository;
    private final CommunityRepository communityRepository;
    private final MembershipService membershipService;

    public FlagService(FlagRepository flagRepository,
                       CommunityRepository communityRepository,
                       MembershipService membershipService) {
        this.flagRepository = flagRepository;
        this.communityRepository = communityRepository;
        this.membershipService = membershipService;
    }

    /**
     * Opens a new flag for a community incident. Only the manager is required
     * to act; the reporter is optional (null reports a future system-generated
     * flag). occurredAt is stamped by the server clock; client timestamps are
     * never trusted.
     */
    @Transactional
    public Flag createFlag(Long communityId, Transaction transaction, FlagType flagType,
                           User reporter, String note, User manager) {
        Community community = findCommunityOrThrow(communityId);
        requireActiveManager(manager, community);

        if (flagType == null) {
            throw new BusinessRuleViolationException("A flag type is required");
        }
        if (transaction != null
                && (transaction.getCommunity() == null
                    || !communityId.equals(transaction.getCommunity().getId()))) {
            throw new BusinessRuleViolationException(
                    "The flagged transaction does not belong to this community");
        }

        Flag flag = new Flag();
        flag.setCommunity(community);
        flag.setTransaction(transaction);
        flag.setFlagType(flagType);
        flag.setStatus(FlagStatus.OPEN);
        flag.setReporter(reporter);
        flag.setNote(normalizeNote(note));
        flag.setOccurredAt(LocalDateTime.now());
        return flagRepository.save(flag);
    }

    @Transactional
    public Flag updateStatus(Long communityId, Long flagId, FlagStatus status, User manager) {
        if (status == null) {
            throw new BusinessRuleViolationException("A status is required");
        }
        Community community = findCommunityOrThrow(communityId);
        requireActiveManager(manager, community);

        Flag flag = findFlagOwnedByCommunity(communityId, flagId);
        flag.setStatus(status);
        return flagRepository.save(flag);
    }

    /**
     * Assigns (or, with a null assignee, unassigns) a manager of the community
     * responsible for the flag.
     */
    @Transactional
    public Flag assignAssignee(Long communityId, Long flagId, User assignee, User manager) {
        Community community = findCommunityOrThrow(communityId);
        requireActiveManager(manager, community);
        if (assignee != null && !membershipService.isActiveManager(assignee.getId(), communityId)) {
            throw new UnauthorizedException(
                    "The assignee must be an active manager of this community");
        }

        Flag flag = findFlagOwnedByCommunity(communityId, flagId);
        flag.setAssignee(assignee);
        return flagRepository.save(flag);
    }

    @Transactional
    public Flag updateNote(Long communityId, Long flagId, String note, User manager) {
        Community community = findCommunityOrThrow(communityId);
        requireActiveManager(manager, community);

        Flag flag = findFlagOwnedByCommunity(communityId, flagId);
        flag.setNote(normalizeNote(note));
        return flagRepository.save(flag);
    }

    public List<Flag> listByCommunity(Long communityId, User manager) {
        requireManagerOf(communityId, manager);
        return flagRepository.findByCommunityId(communityId);
    }

    public List<Flag> listByCommunityAndStatus(Long communityId, FlagStatus status, User manager) {
        if (status == null) {
            throw new BusinessRuleViolationException("A status is required");
        }
        requireManagerOf(communityId, manager);
        return flagRepository.findByCommunityIdAndStatus(communityId, status);
    }

    public List<Flag> listByCommunityAndFlagType(Long communityId, FlagType flagType, User manager) {
        if (flagType == null) {
            throw new BusinessRuleViolationException("A flag type is required");
        }
        requireManagerOf(communityId, manager);
        return flagRepository.findByCommunityIdAndFlagType(communityId, flagType);
    }

    public List<Flag> listByTransaction(Long communityId, Long transactionId, User manager) {
        requireManagerOf(communityId, manager);
        if (transactionId == null) {
            throw new BusinessRuleViolationException("A transaction id is required");
        }
        return flagRepository.findByCommunityIdAndTransactionId(communityId, transactionId);
    }

    public Flag getFlag(Long communityId, Long flagId, User manager) {
        requireManagerOf(communityId, manager);
        return findFlagOwnedByCommunity(communityId, flagId);
    }

    private void requireManagerOf(Long communityId, User manager) {
        Community community = findCommunityOrThrow(communityId);
        requireActiveManager(manager, community);
    }

    private void requireActiveManager(User user, Community community) {
        if (user == null || community.getId() == null
                || !membershipService.isActiveManager(user.getId(), community.getId())) {
            throw new UnauthorizedException("Only an active manager can manage community flags");
        }
    }

    private Community findCommunityOrThrow(Long communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found with id: " + communityId));
    }

    private Flag findFlagOwnedByCommunity(Long communityId, Long flagId) {
        Flag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new ResourceNotFoundException("Flag not found with id: " + flagId));
        Long ownerCommunityId = flag.getCommunity() != null ? flag.getCommunity().getId() : null;
        if (ownerCommunityId == null || !ownerCommunityId.equals(communityId)) {
            throw new ResourceNotFoundException("Flag not found with id: " + flagId + " in community " + communityId);
        }
        return flag;
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new BusinessRuleViolationException(
                    "Flag note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        return trimmed;
    }
}