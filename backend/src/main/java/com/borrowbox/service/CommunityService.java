package com.borrowbox.service;

import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.dto.CommunityResponse;
import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityAdmissionMode;
import com.borrowbox.entity.CommunityStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@Service
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipService membershipService;

    public CommunityService(CommunityRepository communityRepository,
                            MembershipRepository membershipRepository,
                            MembershipService membershipService) {
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.membershipService = membershipService;
    }

    @Transactional
    public CommunityResponse createCommunity(CommunityCreateRequest request, User creator) {
        if (creator == null) {
            throw new BusinessRuleViolationException("Authenticated user is required to create a community");
        }

        String normalized = activeNameKey(request.name());

        if (communityRepository.existsByCreatedByIdAndActiveNameKey(creator.getId(), normalized)) {
            throw new BusinessRuleViolationException(
                    "You already have an active community named '" + request.name() + "'");
        }

        Community community = new Community();
        community.setName(request.name());
        community.setDescription(request.description());
        community.setType(request.type());
        community.setStatus(CommunityStatus.ACTIVE);
        CommunityAdmissionMode mode = request.admissionMode();
        if (mode == null) {
            mode = CommunityAdmissionMode.MANAGER_APPROVAL;
        }
        community.setAdmissionMode(mode);
        community.setCreatedBy(creator);
        community.setLocationLatitude(request.locationLatitude());
        community.setLocationLongitude(request.locationLongitude());
        community.setLocationRadiusM(request.locationRadiusM());
        community.setActiveNameKey(normalized);

        Community saved = communityRepository.save(community);

        Membership creatorMembership = new Membership();
        creatorMembership.setUser(creator);
        creatorMembership.setCommunity(saved);
        creatorMembership.setRole(MembershipRole.MANAGER);
        creatorMembership.setStatus(MembershipStatus.ACTIVE);
        creatorMembership.setVerificationMethod(MembershipVerificationMethod.ADMIN);
        creatorMembership.setVerifiedBy(creator);
        creatorMembership.setVerifiedAt(LocalDateTime.now());
        creatorMembership.setJoinedAt(LocalDateTime.now());
        creatorMembership.setContextMetadata(new HashMap<>());
        membershipRepository.save(creatorMembership);
        saved.getMemberships().add(creatorMembership);

        return toResponse(saved, creator);
    }

    @Transactional(readOnly = true)
    public CommunityResponse getCommunityById(Long id, User viewer) {
        Community community = findOrThrow(id);
        return toResponse(community, viewer);
    }

    @Transactional(readOnly = true)
    public List<CommunityResponse> listCommunitiesForUser(User currentUser) {
        List<Community> communities = communityRepository.findCommunitiesForUser(currentUser.getId());
        List<CommunityResponse> responses = new ArrayList<>();
        for (Community community : communities) {
            responses.add(toResponse(community, currentUser));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> listMembers(Long communityId, User requester) {
        findOrThrow(communityId);
        if (requester == null || !membershipService.isActiveMember(requester.getId(), communityId)) {
            throw new UnauthorizedException(
                    "You must be an active member to view this community's members");
        }
        return membershipService.listForCommunity(communityId);
    }

    private Community findOrThrow(Long id) {
        return communityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found with id: " + id));
    }

    private String activeNameKey(String name) {
        return name == null ? null : name.trim().toLowerCase(Locale.ROOT);
    }

    private CommunityResponse toResponse(Community community, User viewer) {
        int membershipCount = community.getMemberships().size();
        boolean isManager = viewer != null
                && membershipService.isActiveManager(viewer.getId(), community.getId());
        return new CommunityResponse(
                community.getId(),
                community.getName(),
                community.getDescription(),
                community.getType(),
                community.getStatus(),
                community.getAdmissionMode(),
                membershipCount,
                isManager
        );
    }
}
