package com.borrowbox.service;

import com.borrowbox.dto.CommunityJoinRequest;
import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.AdmissionDecision;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityAdmissionMode;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.MembershipVerificationMethod;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MembershipService {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final MembershipRepository membershipRepository;
    private final CommunityRepository communityRepository;

    public MembershipService(MembershipRepository membershipRepository,
                             CommunityRepository communityRepository) {
        this.membershipRepository = membershipRepository;
        this.communityRepository = communityRepository;
    }

    public List<MembershipResponse> listForUser(Long userId) {
        return membershipRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<MembershipResponse> listForCommunity(Long communityId) {
        return membershipRepository.findByCommunityId(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<Membership> findByUserAndCommunity(Long userId, Long communityId) {
        return membershipRepository.findByUserIdAndCommunityId(userId, communityId);
    }

    public boolean isActiveMember(Long userId, Long communityId) {
        return findByUserAndCommunity(userId, communityId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .orElse(false);
    }

    public boolean isActiveManager(Long userId, Long communityId) {
        return findByUserAndCommunity(userId, communityId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE
                        && m.getRole() == MembershipRole.MANAGER)
                .orElse(false);
    }

    /**
     * Community admission entry point. Handles both MANAGER_APPROVAL and
     * LOCATION_VERIFIED join flows for a fresh join or a rejoin.
     */
    @Transactional
    public MembershipResponse joinCommunity(User user, Long communityId, CommunityJoinRequest request) {
        if (user == null) {
            throw new UnauthorizedException("An authenticated user is required to join a community");
        }
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found with id: " + communityId));

        Optional<Membership> existing = membershipRepository.findByUserIdAndCommunityId(user.getId(), communityId);
        Membership membership;
        if (existing.isPresent()) {
            membership = existing.get();
            MembershipStatus status = membership.getStatus();
            if (status == MembershipStatus.ACTIVE) {
                throw new BusinessRuleViolationException("Already a member of this community");
            }
            if (status == MembershipStatus.PENDING) {
                throw new BusinessRuleViolationException("Join request already pending");
            }
            // LEFT or REJECTED: rejoin reuses the same row.
            resetVerificationFields(membership);
            membership.setRole(MembershipRole.MEMBER); // rejoin role is always MEMBER
        } else {
            membership = new Membership();
            membership.setUser(user);
            membership.setCommunity(community);
            membership.setRole(MembershipRole.MEMBER);
        }
        membership.setContextMetadata(request.contextMetadata());

        applyAdmissionPath(membership, community, request);

        try {
            membershipRepository.save(membership);
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(user_id, community_id) is the authoritative guard against
            // concurrent duplicate joins racing past the check above.
            throw new BusinessRuleViolationException("Join request already pending");
        }
        return toResponse(membership);
    }

    /**
     * Manager-only view of pending admissions for a community.
     */
    public List<MembershipResponse> listPendingForCommunity(Long requesterId, Long communityId) {
        requireActiveManager(requesterId, communityId);
        return membershipRepository.findByCommunityId(communityId).stream()
                .filter(m -> m.getStatus() == MembershipStatus.PENDING)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Manager approves or rejects a pending admission.
     */
    @Transactional
    public MembershipResponse decide(Long membershipId, AdmissionDecision decision, User manager) {
        Membership membership = membershipRepository.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found with id: " + membershipId));

        Long communityId = membership.getCommunity() != null ? membership.getCommunity().getId() : null;
        if (communityId == null || !isActiveManager(manager.getId(), communityId)) {
            throw new UnauthorizedException("Only an active manager can review membership requests");
        }
        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw new BusinessRuleViolationException("This request is not pending");
        }

        LocalDateTime now = LocalDateTime.now();
        if (decision == AdmissionDecision.APPROVE) {
            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setVerificationMethod(MembershipVerificationMethod.MANAGER_APPROVAL);
            membership.setVerifiedBy(manager);
            membership.setVerifiedAt(now);
            membership.setJoinedAt(now);
        } else {
            membership.setStatus(MembershipStatus.REJECTED);
        }
        membershipRepository.save(membership);
        return toResponse(membership);
    }

    /**
     * Member leaves the community. ACTIVE -> LEFT preserves the audit-trail
     * fields (role, joinedAt, verifiedBy, verifiedAt, verificationMethod);
     * verification fields are reset only on a later rejoin.
     */
    @Transactional
    public MembershipResponse leave(Long userId, Long communityId) {
        Membership membership = membershipRepository.findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow(() -> new BusinessRuleViolationException("You are not a member of this community"));
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new BusinessRuleViolationException("You are not an active member of this community");
        }
        if (membership.getRole() == MembershipRole.MANAGER) {
            List<Membership> activeManagers = membershipRepository.findActiveManagersForUpdate(communityId);
            if (activeManagers.size() <= 1) {
                throw new BusinessRuleViolationException(
                        "You cannot leave because you are the last active manager of this community");
            }
        }
        membership.setStatus(MembershipStatus.LEFT);
        membershipRepository.save(membership);
        return toResponse(membership);
    }

    private void applyAdmissionPath(Membership membership, Community community, CommunityJoinRequest request) {
        if (community.getAdmissionMode() == CommunityAdmissionMode.LOCATION_VERIFIED) {
            applyLocationVerified(membership, community, request);
        } else {
            applyManagerApproval(membership);
        }
    }

    private void applyManagerApproval(Membership membership) {
        membership.setStatus(MembershipStatus.PENDING);
        membership.setRole(MembershipRole.MEMBER);
        membership.setVerificationMethod(MembershipVerificationMethod.MANAGER_APPROVAL);
        membership.setVerifiedBy(null);
        membership.setVerifiedAt(null);
        membership.setJoinedAt(null);
    }

    private void applyLocationVerified(Membership membership, Community community, CommunityJoinRequest request) {
        if (community.getLocationLatitude() == null
                || community.getLocationLongitude() == null
                || community.getLocationRadiusM() == null
                || community.getLocationRadiusM() <= 0) {
            throw new BusinessRuleViolationException(
                    "This community has no valid location configured for location-verified admission");
        }
        if (request == null || request.latitude() == null || request.longitude() == null) {
            throw new BusinessRuleViolationException(
                    "Latitude and longitude are required to join a location-verified community");
        }

        double distanceMeters = haversineMeters(
                community.getLocationLatitude().doubleValue(),
                community.getLocationLongitude().doubleValue(),
                request.latitude().doubleValue(),
                request.longitude().doubleValue());

        LocalDateTime now = LocalDateTime.now();
        if (distanceMeters <= community.getLocationRadiusM()) {
            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setRole(MembershipRole.MEMBER);
            membership.setVerificationMethod(MembershipVerificationMethod.LOCATION);
            membership.setVerifiedBy(null);
            membership.setVerifiedAt(now);
            membership.setJoinedAt(now);
        } else {
            membership.setStatus(MembershipStatus.PENDING);
            membership.setRole(MembershipRole.MEMBER);
            membership.setVerificationMethod(MembershipVerificationMethod.MANAGER_APPROVAL);
            membership.setVerifiedBy(null);
            membership.setVerifiedAt(null);
            membership.setJoinedAt(null);
        }
    }

    private void resetVerificationFields(Membership membership) {
        membership.setVerifiedBy(null);
        membership.setVerificationMethod(null);
        membership.setVerifiedAt(null);
        membership.setJoinedAt(null);
    }

    private void requireActiveManager(Long userId, Long communityId) {
        if (!isActiveManager(userId, communityId)) {
            throw new UnauthorizedException("Only an active manager can view pending memberships");
        }
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private MembershipResponse toResponse(Membership m) {
        return new MembershipResponse(
                m.getId(),
                m.getUser() != null ? m.getUser().getId() : null,
                m.getCommunity() != null ? m.getCommunity().getId() : null,
                m.getUser() != null ? m.getUser().getFullName() : null,
                m.getCommunity() != null ? m.getCommunity().getName() : null,
                m.getRole(),
                m.getStatus(),
                m.getVerificationMethod(),
                m.getVerifiedAt(),
                m.getJoinedAt(),
                m.getVerifiedBy() != null ? m.getVerifiedBy().getId() : null,
                m.getContextMetadata()
        );
    }
}
