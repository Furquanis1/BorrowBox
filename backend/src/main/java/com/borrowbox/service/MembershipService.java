package com.borrowbox.service;

import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.repository.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MembershipService {

    private final MembershipRepository membershipRepository;

    public MembershipService(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
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
                m.getContextMetadata()
        );
    }
}
