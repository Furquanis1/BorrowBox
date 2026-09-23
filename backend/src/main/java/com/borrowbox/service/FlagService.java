package com.borrowbox.service;

import com.borrowbox.dto.FlagUpdateRequest;
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
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
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
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public FlagService(FlagRepository flagRepository,
                       CommunityRepository communityRepository,
                       MembershipService membershipService,
                       TransactionRepository transactionRepository,
                       UserRepository userRepository) {
        this.flagRepository = flagRepository;
        this.communityRepository = communityRepository;
        this.membershipService = membershipService;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
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
        return hydrate(flagRepository.save(flag));
    }

    /**
     * V2.4.2: opens a flag from a transaction id. The reporting manager is the
     * authenticated actor; no reporter id is ever accepted from the client.
     * A null transactionId opens a manual flag not tied to a loan.
     */
    @Transactional
    public Flag createFlag(Long communityId, Long transactionId, FlagType flagType,
                           String note, User manager) {
        Transaction transaction = null;
        if (transactionId != null) {
            transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + transactionId));
        }
        return createFlag(communityId, transaction, flagType, manager, note, manager);
    }

    /**
     * V2.4.2: consolidated PATCH — status, assignee (or unassign), and note may
     * be updated independently. Request fields set no-change when omitted.
     */
    @Transactional
    public Flag updateFlag(Long communityId, Long flagId, FlagUpdateRequest request, User manager) {
        if (request == null) {
            throw new BusinessRuleViolationException("A flag update is required");
        }
        if (Boolean.TRUE.equals(request.clearAssignee()) && request.assigneeId() != null) {
            throw new BusinessRuleViolationException(
                    "A flag cannot be assigned and unassigned in the same update");
        }
        Community community = findCommunityOrThrow(communityId);
        requireActiveManager(manager, community);

        Flag flag = findFlagOwnedByCommunity(communityId, flagId);
        if (request.status() != null) {
            flag.setStatus(request.status());
        }
        if (request.assigneeId() != null) {
            User assignee = userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.assigneeId()));
            if (!membershipService.isActiveManager(assignee.getId(), communityId)) {
                throw new UnauthorizedException(
                        "The assignee must be an active manager of this community");
            }
            flag.setAssignee(assignee);
        } else if (Boolean.TRUE.equals(request.clearAssignee())) {
            flag.setAssignee(null);
        }
        if (request.note() != null) {
            flag.setNote(normalizeNote(request.note()));
        }
        return hydrate(flagRepository.save(flag));
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
        return hydrate(flagRepository.save(flag));
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
        return hydrate(flagRepository.save(flag));
    }

    @Transactional
    public Flag updateNote(Long communityId, Long flagId, String note, User manager) {
        Community community = findCommunityOrThrow(communityId);
        requireActiveManager(manager, community);

        Flag flag = findFlagOwnedByCommunity(communityId, flagId);
        flag.setNote(normalizeNote(note));
        return hydrate(flagRepository.save(flag));
    }

    public List<Flag> listByCommunity(Long communityId, User manager) {
        requireManagerOf(communityId, manager);
        List<Flag> flags = flagRepository.findByCommunityId(communityId);
        flags.forEach(this::hydrate);
        return flags;
    }

    public List<Flag> listByCommunityAndStatus(Long communityId, FlagStatus status, User manager) {
        if (status == null) {
            throw new BusinessRuleViolationException("A status is required");
        }
        requireManagerOf(communityId, manager);
        List<Flag> flags = flagRepository.findByCommunityIdAndStatus(communityId, status);
        flags.forEach(this::hydrate);
        return flags;
    }

    public List<Flag> listByCommunityAndFlagType(Long communityId, FlagType flagType, User manager) {
        if (flagType == null) {
            throw new BusinessRuleViolationException("A flag type is required");
        }
        requireManagerOf(communityId, manager);
        List<Flag> flags = flagRepository.findByCommunityIdAndFlagType(communityId, flagType);
        flags.forEach(this::hydrate);
        return flags;
    }

    public List<Flag> listByTransaction(Long communityId, Long transactionId, User manager) {
        requireManagerOf(communityId, manager);
        if (transactionId == null) {
            throw new BusinessRuleViolationException("A transaction id is required");
        }
        List<Flag> flags = flagRepository.findByCommunityIdAndTransactionId(communityId, transactionId);
        flags.forEach(this::hydrate);
        return flags;
    }

    /**
     * V2.4.2 combined filter; every filter is optional. With no filters the
     * community's full flag list is returned, newest first.
     */
    public List<Flag> listFiltered(Long communityId, FlagStatus status, FlagType flagType,
                                   Long transactionId, User manager) {
        requireManagerOf(communityId, manager);
        List<Flag> flags = flagRepository.findFiltered(communityId, status, flagType, transactionId);
        flags.forEach(this::hydrate);
        return flags;
    }

    public List<Flag> recentFlags(Long communityId, User manager) {
        requireManagerOf(communityId, manager);
        List<Flag> flags = flagRepository.findTop10ByCommunityIdOrderByOccurredAtDescIdDesc(communityId);
        flags.forEach(this::hydrate);
        return flags;
    }

    public long countOpen(Long communityId) {
        return flagRepository.countByCommunityIdAndStatus(communityId, FlagStatus.OPEN);
    }

    public Flag getFlag(Long communityId, Long flagId, User manager) {
        requireManagerOf(communityId, manager);
        return hydrate(findFlagOwnedByCommunity(communityId, flagId));
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

    /**
     * Ensures the lazy assignee association is loaded before a Flag leaves the
     * transactional boundary, because {@code FlagResponse.from} reads
     * assignee.getFullName() and open-in-view is disabled. Touching the getter
     * is a no-op for already-initialized or non-proxy assignees, so the method
     * is safe against mocks and plain entities in unit tests.
     */
    private Flag hydrate(Flag flag) {
        if (flag != null && flag.getAssignee() != null) {
            flag.getAssignee().getFullName();
        }
        return flag;
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