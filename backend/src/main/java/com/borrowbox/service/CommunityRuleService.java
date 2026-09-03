package com.borrowbox.service;

import com.borrowbox.dto.CommunityRuleRequest;
import com.borrowbox.dto.CommunityRuleResponse;
import com.borrowbox.dto.CommunityRuleUpdateRequest;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityRule;
import com.borrowbox.entity.CommunityRuleType;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.CommunityRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * V2.1.3 Community Rules Foundation.
 *
 * Rules are membership/admission policy only. This slice provides storage and
 * query hooks; no rule is enforced at join time and no transaction/borrowing
 * rule types exist. The single-active invariant (at most one ACTIVE rule per
 * (community, ruleType)) is enforced under a PESSIMISTIC_WRITE lock on the
 * Community row, never via an unlocked check-then-act sequence.
 */
@Service
@Transactional(readOnly = true)
public class CommunityRuleService {

    private final CommunityRuleRepository communityRuleRepository;
    private final CommunityRepository communityRepository;
    private final MembershipService membershipService;

    public CommunityRuleService(CommunityRuleRepository communityRuleRepository,
                                CommunityRepository communityRepository,
                                MembershipService membershipService) {
        this.communityRuleRepository = communityRuleRepository;
        this.communityRepository = communityRepository;
        this.membershipService = membershipService;
    }

    @Transactional
    public CommunityRuleResponse createRule(Long communityId, CommunityRuleRequest request, User manager) {
        Community community = lockedActiveCommunityForMutation(communityId, manager);

        // Single-active: archive any existing ACTIVE same-type rule before
        // inserting the new ACTIVE rule. Evaluated against the locked community.
        archiveActiveSameType(communityId, request.ruleType());

        CommunityRule rule = new CommunityRule();
        rule.setCommunity(community);
        rule.setRuleType(request.ruleType());
        rule.setValue(request.value());
        rule.setStatus(CommunityStatus.ACTIVE);
        rule.setCreatedBy(manager);
        rule.setUpdatedBy(manager);
        CommunityRule saved = communityRuleRepository.save(rule);
        return toResponse(saved);
    }

    @Transactional
    public CommunityRuleResponse updateRule(Long communityId, Long ruleId,
                                            CommunityRuleUpdateRequest request, User manager) {
        lockedActiveCommunityForMutation(communityId, manager);

        CommunityRule rule = findRuleOwnedByCommunity(communityId, ruleId);

        if (Boolean.TRUE.equals(request.active())) {
            // Behave exactly like the explicit activate endpoint: archive any
            // conflicting ACTIVE same-type rule, then activate the target.
            archiveActiveSameTypeExcluding(communityId, rule.getRuleType(), ruleId);
            rule.setStatus(CommunityStatus.ACTIVE);
        } else {
            rule.setStatus(CommunityStatus.ARCHIVED);
        }
        rule.setValue(request.value());
        rule.setUpdatedBy(manager);
        return toResponse(communityRuleRepository.save(rule));
    }

    @Transactional
    public CommunityRuleResponse activateRule(Long communityId, Long ruleId, User manager) {
        lockedActiveCommunityForMutation(communityId, manager);

        CommunityRule rule = findRuleOwnedByCommunity(communityId, ruleId);

        archiveActiveSameTypeExcluding(communityId, rule.getRuleType(), ruleId);
        rule.setStatus(CommunityStatus.ACTIVE);
        rule.setUpdatedBy(manager);
        return toResponse(communityRuleRepository.save(rule));
    }

    @Transactional
    public CommunityRuleResponse deactivateRule(Long communityId, Long ruleId, User manager) {
        lockedActiveCommunityForMutation(communityId, manager);

        CommunityRule rule = findRuleOwnedByCommunity(communityId, ruleId);

        rule.setStatus(CommunityStatus.ARCHIVED);
        rule.setUpdatedBy(manager);
        return toResponse(communityRuleRepository.save(rule));
    }

    /**
     * Manager-only: all rules for a community (ACTIVE + ARCHIVED), ordered by rule type.
     */
    public List<CommunityRuleResponse> listRulesForCommunity(Long communityId, User manager) {
        Community community = findCommunityOrThrow(communityId);
        requireActiveManager(manager, community);
        return communityRuleRepository.findByCommunityId(communityId).stream()
                .sorted((a, b) -> a.getRuleType().name().compareTo(b.getRuleType().name()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * ACTIVE MEMBER only: the community's ACTIVE membership-policy rules.
     * No public/pre-join access. Read-only; not enforced at join time.
     */
    public List<CommunityRuleResponse> listActiveRules(Long communityId, User member) {
        Community community = findCommunityOrThrow(communityId);
        if (member == null || !membershipService.isActiveMember(member.getId(), communityId)) {
            throw new UnauthorizedException(
                    "You must be an active member to view this community's rules");
        }
        return communityRuleRepository.findByCommunityIdAndStatus(communityId, CommunityStatus.ACTIVE).stream()
                .sorted((a, b) -> a.getRuleType().name().compareTo(b.getRuleType().name()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Reserved integration hook for a future enforcement slice. Deliberately
     * NOT called from joinCommunity or decide in this slice.
     */
    public List<CommunityRule> activeAdmissionRules(Long communityId) {
        return communityRuleRepository.findByCommunityIdAndStatus(communityId, CommunityStatus.ACTIVE);
    }

    /**
     * Loads the Community with PESSIMISTIC_WRITE and verifies it is ACTIVE and
     * the caller is an ACTIVE manager. Returns the locked community. All rule
     * mutations that affect rule state must go through this first.
     */
    private Community lockedActiveCommunityForMutation(Long communityId, User manager) {
        Community community = communityRepository.findByIdForUpdate(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found with id: " + communityId));
        requireActiveManager(manager, community);
        if (community.getStatus() != CommunityStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "Rules of an archived community cannot be modified");
        }
        return community;
    }

    private void requireActiveManager(User user, Community community) {
        if (user == null || community.getId() == null
                || !membershipService.isActiveManager(user.getId(), community.getId())) {
            throw new UnauthorizedException("Only an active manager can manage community rules");
        }
    }

    private CommunityRule findRuleOwnedByCommunity(Long communityId, Long ruleId) {
        CommunityRule rule = communityRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found with id: " + ruleId));
        Long ownerCommunityId = rule.getCommunity() != null ? rule.getCommunity().getId() : null;
        if (ownerCommunityId == null || !ownerCommunityId.equals(communityId)) {
            throw new ResourceNotFoundException("Rule not found with id: " + ruleId + " in community " + communityId);
        }
        return rule;
    }

    private void archiveActiveSameType(Long communityId, CommunityRuleType ruleType) {
        for (CommunityRule rule : communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                communityId, ruleType, CommunityStatus.ACTIVE)) {
            rule.setStatus(CommunityStatus.ARCHIVED);
            communityRuleRepository.save(rule);
        }
    }

    private void archiveActiveSameTypeExcluding(Long communityId, CommunityRuleType ruleType, Long excludeRuleId) {
        for (CommunityRule rule : communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                communityId, ruleType, CommunityStatus.ACTIVE)) {
            if (rule.getId().equals(excludeRuleId)) {
                continue;
            }
            rule.setStatus(CommunityStatus.ARCHIVED);
            communityRuleRepository.save(rule);
        }
    }

    private Community findCommunityOrThrow(Long communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found with id: " + communityId));
    }

    private CommunityRuleResponse toResponse(CommunityRule rule) {
        return new CommunityRuleResponse(
                rule.getId(),
                rule.getCommunity() != null ? rule.getCommunity().getId() : null,
                rule.getRuleType(),
                rule.getValue(),
                rule.getStatus(),
                rule.getCreatedBy() != null ? rule.getCreatedBy().getId() : null,
                rule.getUpdatedBy() != null ? rule.getUpdatedBy().getId() : null,
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}