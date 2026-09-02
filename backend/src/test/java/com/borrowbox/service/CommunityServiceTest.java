package com.borrowbox.service;

import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.dto.CommunityResponse;
import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityAdmissionMode;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CommunityServiceTest {

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private MembershipService membershipService;

    private CommunityService communityService;

    private User creator;

    @BeforeEach
    void setUp() {
        communityService = new CommunityService(communityRepository, membershipRepository, membershipService);
        creator = new User("Ahmed", "ahmed@example.com");
        creator.setId(100L);
    }

    @Test
    void createCommunityMakesCreatorActiveManagerAtomically() {
        when(communityRepository.existsByCreatedByIdAndActiveNameKey(100L, "cse department"))
                .thenReturn(false);
        Community saved = new Community();
        saved.setId(500L);
        saved.setName("CSE Department");
        saved.setType(CommunityType.COLLEGE);
        saved.setStatus(CommunityStatus.ACTIVE);
        saved.setAdmissionMode(CommunityAdmissionMode.MANAGER_APPROVAL);
        saved.setCreatedBy(creator);
        when(communityRepository.save(any(Community.class))).thenReturn(saved);

        CommunityResponse response = communityService.createCommunity(
                new CommunityCreateRequest("CSE Department", "desc", CommunityType.COLLEGE, null, null, null, null),
                creator);

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.membershipCount()).isEqualTo(1);
        assertThat(response.isManager()).isFalse(); // membership not yet "seen" by mock service

        ArgumentCaptor<Membership> membershipCaptor = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        Membership creatorMembership = membershipCaptor.getValue();
        assertThat(creatorMembership.getRole()).isEqualTo(MembershipRole.MANAGER);
        assertThat(creatorMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(creatorMembership.getUser().getId()).isEqualTo(100L);
        assertThat(creatorMembership.getCommunity().getId()).isEqualTo(500L);
    }

    @Test
    void defaultAdmissionModeIsManagerApproval() {
        when(communityRepository.existsByCreatedByIdAndActiveNameKey(100L, "office"))
                .thenReturn(false);
        Community saved = new Community();
        saved.setId(600L);
        saved.setName("Office");
        saved.setType(CommunityType.OFFICE);
        saved.setStatus(CommunityStatus.ACTIVE);
        saved.setAdmissionMode(CommunityAdmissionMode.MANAGER_APPROVAL);
        saved.setCreatedBy(creator);
        when(communityRepository.save(any(Community.class))).thenReturn(saved);

        communityService.createCommunity(
                new CommunityCreateRequest("Office", null, CommunityType.OFFICE, null, null, null, null),
                creator);

        ArgumentCaptor<Community> communityCaptor = ArgumentCaptor.forClass(Community.class);
        verify(communityRepository).save(communityCaptor.capture());
        assertThat(communityCaptor.getValue().getAdmissionMode())
                .isEqualTo(CommunityAdmissionMode.MANAGER_APPROVAL);
        assertThat(communityCaptor.getValue().getActiveNameKey()).isEqualTo("office");
    }

    @Test
    void duplicateActiveNameForSameCreatorThrows() {
        when(communityRepository.existsByCreatedByIdAndActiveNameKey(100L, "cse department"))
                .thenReturn(true);

        assertThatThrownBy(() -> communityService.createCommunity(
                new CommunityCreateRequest("  CSE Department  ", "desc", CommunityType.COLLEGE, null, null, null, null),
                creator))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void nullCreatorThrows() {
        assertThatThrownBy(() -> communityService.createCommunity(
                new CommunityCreateRequest("X", null, CommunityType.OTHER, null, null, null, null),
                null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void activeMemberCanReadMemberList() {
        Community community = new Community();
        community.setId(2L);
        when(communityRepository.findById(2L)).thenReturn(java.util.Optional.of(community));
        when(membershipService.isActiveMember(100L, 2L)).thenReturn(true);
        MembershipResponse member = new MembershipResponse(
                1L, 100L, 2L, "Ahmed", "CSE", MembershipRole.MEMBER,
                MembershipStatus.ACTIVE, null, null, null, null, java.util.Map.of());
        when(membershipService.listForCommunity(2L)).thenReturn(java.util.List.of(member));

        List<MembershipResponse> members = communityService.listMembers(2L, creator);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).userId()).isEqualTo(100L);
    }

    @Test
    void activeManagerCanReadMemberList() {
        Community community = new Community();
        community.setId(3L);
        when(communityRepository.findById(3L)).thenReturn(java.util.Optional.of(community));
        when(membershipService.isActiveMember(100L, 3L)).thenReturn(true);
        MembershipResponse manager = new MembershipResponse(
                1L, 100L, 3L, "Ahmed", "Office", MembershipRole.MANAGER,
                MembershipStatus.ACTIVE, null, null, null, 100L, java.util.Map.of());
        when(membershipService.listForCommunity(3L)).thenReturn(java.util.List.of(manager));

        List<MembershipResponse> members = communityService.listMembers(3L, creator);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).role()).isEqualTo(MembershipRole.MANAGER);
    }

    @Test
    void nonMemberCannotReadMemberList() {
        Community community = new Community();
        community.setId(4L);
        when(communityRepository.findById(4L)).thenReturn(java.util.Optional.of(community));
        when(membershipService.isActiveMember(100L, 4L)).thenReturn(false);

        assertThatThrownBy(() -> communityService.listMembers(4L, creator))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void nullRequesterCannotReadMemberList() {
        Community community = new Community();
        community.setId(5L);
        when(communityRepository.findById(5L)).thenReturn(java.util.Optional.of(community));

        assertThatThrownBy(() -> communityService.listMembers(5L, null))
                .isInstanceOf(UnauthorizedException.class);
    }
}
