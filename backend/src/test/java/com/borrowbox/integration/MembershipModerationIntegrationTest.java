package com.borrowbox.integration;

import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.Community;
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
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.MembershipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V2.4.2 manager moderation of members against the real database: the
 * suspend / reinstate / remove lifecycle preserves audit fields, never touches
 * roles, blocks the last active manager from being suspended or removed, and
 * is strictly manager-only and community-scoped.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MembershipModerationIntegrationTest {

    @Autowired
    private CommunityService communityService;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private CommunityRepository communityRepository;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private UserRepository userRepository;

    private record World(Community community, User manager) {
    }

    private World world() {
        User manager = V242IntegrationFixture.newUser(userRepository, "ModMgr");
        Community community = V242IntegrationFixture.newCommunity(communityService, communityRepository, manager);
        return new World(community, manager);
    }

    private Membership joinActive(User user, Community community, MembershipRole role) {
        return V242IntegrationFixture.joinActive(membershipRepository, user, community, role);
    }

    private Membership joinPending(User user, Community community) {
        return V242IntegrationFixture.joinPending(membershipRepository, user, community);
    }

    private Membership snapshot(Long id) {
        return membershipRepository.findById(id).orElseThrow();
    }

    @Test
    void suspendReinstateRemoveLifecyclePreservesAuditFields() {
        World w = world();
        User member = V242IntegrationFixture.newUser(userRepository, "ModMember");
        Membership membership = joinActive(member, w.community(), MembershipRole.MEMBER);

        MembershipResponse suspended = membershipService.suspend(membership.getId(), w.manager());
        assertThat(suspended.status()).isEqualTo(MembershipStatus.SUSPENDED);
        assertThat(suspended.role()).isEqualTo(MembershipRole.MEMBER);
        assertThat(suspended.verificationMethod()).isEqualTo(MembershipVerificationMethod.ADMIN);
        assertThat(suspended.joinedAt()).isNotNull();

        MembershipResponse reinstated = membershipService.reinstate(membership.getId(), w.manager());
        assertThat(reinstated.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(reinstated.joinedAt()).isEqualTo(suspended.joinedAt());

        membershipService.suspend(membership.getId(), w.manager());
        MembershipResponse removed = membershipService.removeMember(membership.getId(), w.manager());
        assertThat(removed.status()).isEqualTo(MembershipStatus.LEFT);

        Membership finalRow = snapshot(membership.getId());
        assertThat(finalRow.getRole()).isEqualTo(MembershipRole.MEMBER);
        assertThat(finalRow.getJoinedAt()).isNotNull();
        assertThat(finalRow.getVerifiedBy().getId()).isEqualTo(member.getId());
        assertThat(finalRow.getVerificationMethod()).isEqualTo(MembershipVerificationMethod.ADMIN);
    }

    @Test
    void suspendRequiresActiveAndRejectsPending() {
        World w = world();
        User pendingUser = V242IntegrationFixture.newUser(userRepository, "ModPending");
        Membership pending = joinPending(pendingUser, w.community());

        assertThatThrownBy(() -> membershipService.suspend(pending.getId(), w.manager()))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> membershipService.reinstate(pending.getId(), w.manager()))
                .isInstanceOf(BusinessRuleViolationException.class);

        User member = V242IntegrationFixture.newUser(userRepository, "ModMember2");
        Membership active = joinActive(member, w.community(), MembershipRole.MEMBER);
        assertThatThrownBy(() -> membershipService.reinstate(active.getId(), w.manager()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void lastActiveManagerCannotBeSuspendedOrRemoved() {
        World w = world();
        Membership managerRow = snapshot(
                membershipRepository.findByUserIdAndCommunityId(w.manager().getId(), w.community().getId())
                        .orElseThrow().getId());

        assertThatThrownBy(() -> membershipService.suspend(managerRow.getId(), w.manager()))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> membershipService.removeMember(managerRow.getId(), w.manager()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void secondManagerCanSuspendAndRemoveTheFirst() {
        World w = world();
        User second = V242IntegrationFixture.newUser(userRepository, "ModMgr2");
        joinActive(second, w.community(), MembershipRole.MANAGER);
        Membership firstRow = snapshot(
                membershipRepository.findByUserIdAndCommunityId(w.manager().getId(), w.community().getId())
                        .orElseThrow().getId());

        MembershipResponse suspended = membershipService.suspend(firstRow.getId(), second);
        assertThat(suspended.status()).isEqualTo(MembershipStatus.SUSPENDED);

        membershipService.reinstate(firstRow.getId(), second);
        MembershipResponse removed = membershipService.removeMember(firstRow.getId(), second);
        assertThat(removed.status()).isEqualTo(MembershipStatus.LEFT);
    }

    @Test
    void moderationIsCommunityScopedAndManagerOnly() {
        World a = world();
        User managerB = V242IntegrationFixture.newUser(userRepository, "ModMgrB");
        Community communityB = V242IntegrationFixture.newCommunity(communityService, communityRepository, managerB);
        User memberB = V242IntegrationFixture.newUser(userRepository, "ModMemberB");
        Membership memberBRow = joinActive(memberB, communityB, MembershipRole.MEMBER);

        assertThatThrownBy(() -> membershipService.suspend(memberBRow.getId(), a.manager()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> membershipService.removeMember(memberBRow.getId(), a.manager()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> membershipService.suspend(999_999_999L, a.manager()))
                .isInstanceOf(ResourceNotFoundException.class);

        User plainMember = V242IntegrationFixture.newUser(userRepository, "ModPlain");
        Membership plainRow = joinActive(plainMember, a.community(), MembershipRole.MEMBER);
        User other = V242IntegrationFixture.newUser(userRepository, "ModOther");
        Membership otherRow = joinActive(other, a.community(), MembershipRole.MEMBER);
        assertThatThrownBy(() -> membershipService.suspend(otherRow.getId(), plainMember))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void memberDirectoryFiltersAndRequiresActiveMembership() {
        World w = world();
        User member = V242IntegrationFixture.newUser(userRepository, "ModMember3");
        joinActive(member, w.community(), MembershipRole.MEMBER);
        User pendingUser = V242IntegrationFixture.newUser(userRepository, "ModPending2");
        joinPending(pendingUser, w.community());

        List<MembershipResponse> all = membershipService.listMembers(
                w.manager().getId(), w.community().getId(), null, null);
        assertThat(all).hasSize(3);

        List<MembershipResponse> managers = membershipService.listMembers(
                w.manager().getId(), w.community().getId(), MembershipStatus.ACTIVE, MembershipRole.MANAGER);
        assertThat(managers).hasSize(1);
        assertThat(managers.get(0).userId()).isEqualTo(w.manager().getId());

        List<MembershipResponse> pending = membershipService.listMembers(
                w.manager().getId(), w.community().getId(), MembershipStatus.PENDING, null);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).userId()).isEqualTo(pendingUser.getId());

        User outsider = V242IntegrationFixture.newUser(userRepository, "ModOutsider");
        assertThatThrownBy(() -> membershipService.listMembers(
                outsider.getId(), w.community().getId(), null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void suspendTimestampsAndJoinedAtStayStableAcrossLifecycle() {
        World w = world();
        User member = V242IntegrationFixture.newUser(userRepository, "ModMember4");
        Membership membership = joinActive(member, w.community(), MembershipRole.MEMBER);

        LocalDateTime joinedAtBefore = snapshot(membership.getId()).getJoinedAt();
        LocalDateTime verifiedAtBefore = snapshot(membership.getId()).getVerifiedAt();

        membershipService.suspend(membership.getId(), w.manager());
        membershipService.reinstate(membership.getId(), w.manager());

        Membership after = snapshot(membership.getId());
        assertThat(after.getJoinedAt()).isEqualTo(joinedAtBefore);
        assertThat(after.getVerifiedAt()).isEqualTo(verifiedAtBefore);
        assertThat(after.getVerificationMethod()).isEqualTo(MembershipVerificationMethod.ADMIN);
    }
}