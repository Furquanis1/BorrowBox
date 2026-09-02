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
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MembershipServiceTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private CommunityRepository communityRepository;

    private User user(Long id) {
        User u = new User("U" + id, "u" + id + "@example.com");
        u.setId(id);
        return u;
    }

    private Community community(Long id, CommunityAdmissionMode mode) {
        Community c = new Community();
        c.setId(id);
        c.setName("C" + id);
        c.setType(CommunityType.CLUB);
        c.setStatus(CommunityStatus.ACTIVE);
        c.setAdmissionMode(mode);
        return c;
    }

    private Membership membership(User user, Long communityId, MembershipRole role, MembershipStatus status) {
        Membership m = new Membership();
        m.setUser(user);
        Community c = new Community();
        c.setId(communityId);
        c.setName("C" + communityId);
        m.setCommunity(c);
        m.setRole(role);
        m.setStatus(status);
        return m;
    }

    @Test
    void isActiveMemberReturnsTrueForActiveMembership() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 2L))
                .thenReturn(Optional.of(membership(user(1L), 2L, MembershipRole.MEMBER, MembershipStatus.ACTIVE)));

        assertThat(service.isActiveMember(1L, 2L)).isTrue();
    }

    @Test
    void isActiveMemberReturnsFalseForNonActive() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 2L))
                .thenReturn(Optional.of(membership(user(1L), 2L, MembershipRole.MEMBER, MembershipStatus.LEFT)));

        assertThat(service.isActiveMember(1L, 2L)).isFalse();
    }

    @Test
    void isActiveManagerReturnsTrueOnlyForActiveManager() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 2L))
                .thenReturn(Optional.of(membership(user(1L), 2L, MembershipRole.MANAGER, MembershipStatus.ACTIVE)));

        assertThat(service.isActiveManager(1L, 2L)).isTrue();
    }

    @Test
    void isActiveManagerReturnsFalseForActiveMember() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 2L))
                .thenReturn(Optional.of(membership(user(1L), 2L, MembershipRole.MEMBER, MembershipStatus.ACTIVE)));

        assertThat(service.isActiveManager(1L, 2L)).isFalse();
    }

    @Test
    void isActiveManagerReturnsFalseWhenMembershipAbsent() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 2L))
                .thenReturn(Optional.empty());

        assertThat(service.isActiveManager(1L, 2L)).isFalse();
        assertThat(service.isActiveMember(1L, 2L)).isFalse();
    }

    // ─── MANAGER_APPROVAL join ──────────────────────────────────────────────

    @Test
    void managerApprovalJoinCreatesPendingMembershipWithExactState() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Community c = community(10L, CommunityAdmissionMode.MANAGER_APPROVAL);
        when(communityRepository.findById(10L)).thenReturn(Optional.of(c));
        when(membershipRepository.findByUserIdAndCommunityId(1L, 10L)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipResponse resp = service.joinCommunity(user(1L), 10L, new CommunityJoinRequest(null, null, null));

        assertThat(resp.role()).isEqualTo(MembershipRole.MEMBER);
        assertThat(resp.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(resp.verificationMethod()).isEqualTo(MembershipVerificationMethod.MANAGER_APPROVAL);
        assertThat(resp.verifiedBy()).isNull();
        assertThat(resp.verifiedAt()).isNull();
        assertThat(resp.joinedAt()).isNull();
    }

    @Test
    void managerApprovalJoinWhenAlreadyPendingThrows() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Community c = community(10L, CommunityAdmissionMode.MANAGER_APPROVAL);
        when(communityRepository.findById(10L)).thenReturn(Optional.of(c));
        Membership pending = membership(user(1L), 10L, MembershipRole.MEMBER, MembershipStatus.PENDING);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 10L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.joinCommunity(user(1L), 10L, new CommunityJoinRequest(null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Join request already pending");
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void managerApprovalJoinWhenAlreadyActiveThrows() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Community c = community(10L, CommunityAdmissionMode.MANAGER_APPROVAL);
        when(communityRepository.findById(10L)).thenReturn(Optional.of(c));
        Membership active = membership(user(1L), 10L, MembershipRole.MEMBER, MembershipStatus.ACTIVE);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 10L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.joinCommunity(user(1L), 10L, new CommunityJoinRequest(null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Already a member of this community");
    }

    // ─── LOCATION_VERIFIED join ─────────────────────────────────────────────

    private Community locationCommunity(Long id, String lat, String lon, int radiusM) {
        Community c = community(id, CommunityAdmissionMode.LOCATION_VERIFIED);
        c.setLocationLatitude(new BigDecimal(lat));
        c.setLocationLongitude(new BigDecimal(lon));
        c.setLocationRadiusM(radiusM);
        return c;
    }

    @Test
    void locationJoinInsideRadiusActivatesAutomatically() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Community c = locationCommunity(20L, "29.987400", "31.196800", 500);
        when(communityRepository.findById(20L)).thenReturn(Optional.of(c));
        when(membershipRepository.findByUserIdAndCommunityId(1L, 20L)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipResponse resp = service.joinCommunity(user(1L), 20L,
                new CommunityJoinRequest(new BigDecimal("29.987500"), new BigDecimal("31.196900"), null));

        assertThat(resp.role()).isEqualTo(MembershipRole.MEMBER);
        assertThat(resp.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(resp.verificationMethod()).isEqualTo(MembershipVerificationMethod.LOCATION);
        assertThat(resp.verifiedBy()).isNull();
        assertThat(resp.verifiedAt()).isNotNull();
        assertThat(resp.joinedAt()).isNotNull();
    }

    @Test
    void locationJoinOutsideRadiusGoesPendingForManagerApproval() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Community c = locationCommunity(20L, "29.987400", "31.196800", 100);
        when(communityRepository.findById(20L)).thenReturn(Optional.of(c));
        when(membershipRepository.findByUserIdAndCommunityId(1L, 20L)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipResponse resp = service.joinCommunity(user(1L), 20L,
                new CommunityJoinRequest(new BigDecimal("29.990000"), new BigDecimal("31.200000"), null));

        assertThat(resp.role()).isEqualTo(MembershipRole.MEMBER);
        assertThat(resp.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(resp.verificationMethod()).isEqualTo(MembershipVerificationMethod.MANAGER_APPROVAL);
        assertThat(resp.verifiedBy()).isNull();
        assertThat(resp.verifiedAt()).isNull();
        assertThat(resp.joinedAt()).isNull();
    }

    @Test
    void locationJoinWithoutCoordinatesThrows() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Community c = locationCommunity(20L, "29.987400", "31.196800", 500);
        when(communityRepository.findById(20L)).thenReturn(Optional.of(c));
        when(membershipRepository.findByUserIdAndCommunityId(1L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.joinCommunity(user(1L), 20L, new CommunityJoinRequest(null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void locationJoinWithNoCommunityLocationThrows() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Community c = community(20L, CommunityAdmissionMode.LOCATION_VERIFIED); // no lat/lon/radius
        when(communityRepository.findById(20L)).thenReturn(Optional.of(c));
        when(membershipRepository.findByUserIdAndCommunityId(1L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.joinCommunity(user(1L), 20L,
                new CommunityJoinRequest(new BigDecimal("29.0"), new BigDecimal("31.0"), null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ─── Rejoin ─────────────────────────────────────────────────────────────

    @Test
    void rejoinAfterLeftReusesSameRowAndForcesMemberRole() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Community c = community(30L, CommunityAdmissionMode.MANAGER_APPROVAL);
        when(communityRepository.findById(30L)).thenReturn(Optional.of(c));
        Membership left = membership(user(7L), 30L, MembershipRole.MANAGER, MembershipStatus.LEFT);
        left.setJoinedAt(LocalDateTime.now().minusDays(10));
        left.setVerificationMethod(MembershipVerificationMethod.MANAGER_APPROVAL);
        left.setVerifiedBy(user(7L));
        left.setVerifiedAt(LocalDateTime.now().minusDays(10));
        when(membershipRepository.findByUserIdAndCommunityId(7L, 30L)).thenReturn(Optional.of(left));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipResponse resp = service.joinCommunity(user(7L), 30L, new CommunityJoinRequest(null, null, null));

        assertThat(resp.id()).isEqualTo(left.getId());
        assertThat(resp.role()).isEqualTo(MembershipRole.MEMBER);
        assertThat(resp.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(resp.verificationMethod()).isEqualTo(MembershipVerificationMethod.MANAGER_APPROVAL);
        assertThat(resp.verifiedBy()).isNull();
        assertThat(resp.verifiedAt()).isNull();
        assertThat(resp.joinedAt()).isNull();
    }

    // ─── Decisions ──────────────────────────────────────────────────────────

    @Test
    void approvePendingActivatesMembershipWithManagerVerifiedBy() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        User manager = user(5L);
        User requester = user(6L);
        Membership pending = membership(requester, 40L, MembershipRole.MEMBER, MembershipStatus.PENDING);
        pending.setUser(requester);
        pending.setId(99L);
        when(membershipRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(pending));
        when(membershipRepository.findByUserIdAndCommunityId(5L, 40L))
                .thenReturn(Optional.of(membership(manager, 40L, MembershipRole.MANAGER, MembershipStatus.ACTIVE)));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipResponse resp = service.decide(99L, AdmissionDecision.APPROVE, manager);

        assertThat(resp.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(resp.verificationMethod()).isEqualTo(MembershipVerificationMethod.MANAGER_APPROVAL);
        assertThat(resp.verifiedBy()).isEqualTo(5L);
        assertThat(resp.verifiedAt()).isNotNull();
        assertThat(resp.joinedAt()).isNotNull();
    }

    @Test
    void rejectPendingSetsRejected() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        User manager = user(5L);
        User requester = user(6L);
        Membership pending = membership(requester, 40L, MembershipRole.MEMBER, MembershipStatus.PENDING);
        pending.setId(99L);
        when(membershipRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(pending));
        when(membershipRepository.findByUserIdAndCommunityId(5L, 40L))
                .thenReturn(Optional.of(membership(manager, 40L, MembershipRole.MANAGER, MembershipStatus.ACTIVE)));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipResponse resp = service.decide(99L, AdmissionDecision.REJECT, manager);

        assertThat(resp.status()).isEqualTo(MembershipStatus.REJECTED);
        assertThat(resp.verificationMethod()).isNull();
    }

    @Test
    void decideNonPendingThrows() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        User manager = user(5L);
        User requester = user(6L);
        Membership active = membership(requester, 40L, MembershipRole.MEMBER, MembershipStatus.ACTIVE);
        active.setId(99L);
        when(membershipRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(active));
        when(membershipRepository.findByUserIdAndCommunityId(5L, 40L))
                .thenReturn(Optional.of(membership(manager, 40L, MembershipRole.MANAGER, MembershipStatus.ACTIVE)));

        assertThatThrownBy(() -> service.decide(99L, AdmissionDecision.APPROVE, manager))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void decideByNonManagerThrowsUnauthorized() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        User nonManager = user(8L);
        User requester = user(6L);
        Membership pending = membership(requester, 40L, MembershipRole.MEMBER, MembershipStatus.PENDING);
        pending.setId(99L);
        when(membershipRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(pending));
        when(membershipRepository.findByUserIdAndCommunityId(8L, 40L))
                .thenReturn(Optional.of(membership(nonManager, 40L, MembershipRole.MEMBER, MembershipStatus.ACTIVE)));

        assertThatThrownBy(() -> service.decide(99L, AdmissionDecision.APPROVE, nonManager))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void listPendingRequiresActiveManager() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        when(membershipRepository.findByUserIdAndCommunityId(8L, 40L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listPendingForCommunity(8L, 40L))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void listPendingReturnsOnlyPending() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        when(membershipRepository.findByUserIdAndCommunityId(5L, 40L))
                .thenReturn(Optional.of(membership(user(5L), 40L, MembershipRole.MANAGER, MembershipStatus.ACTIVE)));
        Membership pending = membership(user(6L), 40L, MembershipRole.MEMBER, MembershipStatus.PENDING);
        pending.setId(199L);
        Membership active = membership(user(9L), 40L, MembershipRole.MEMBER, MembershipStatus.ACTIVE);
        when(membershipRepository.findByCommunityId(40L)).thenReturn(List.of(pending, active));

        List<MembershipResponse> result = service.listPendingForCommunity(5L, 40L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(MembershipStatus.PENDING);
    }

    // ─── Leave ──────────────────────────────────────────────────────────────

    @Test
    void leavePreservesAuditTrailFields() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        LocalDateTime joined = LocalDateTime.now().minusDays(20);
        User verifiedBy = user(5L);
        Membership active = membership(user(1L), 40L, MembershipRole.MEMBER, MembershipStatus.ACTIVE);
        active.setJoinedAt(joined);
        active.setVerificationMethod(MembershipVerificationMethod.LOCATION);
        active.setVerifiedBy(verifiedBy);
        active.setVerifiedAt(joined);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 40L)).thenReturn(Optional.of(active));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipResponse resp = service.leave(1L, 40L);

        assertThat(resp.status()).isEqualTo(MembershipStatus.LEFT);
        assertThat(resp.role()).isEqualTo(MembershipRole.MEMBER);
        assertThat(resp.joinedAt()).isEqualTo(joined);
        assertThat(resp.verificationMethod()).isEqualTo(MembershipVerificationMethod.LOCATION);
        assertThat(resp.verifiedBy()).isEqualTo(5L);
        assertThat(resp.verifiedAt()).isEqualTo(joined);
    }

    @Test
    void lastActiveManagerCannotLeave() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Membership manager = membership(user(1L), 40L, MembershipRole.MANAGER, MembershipStatus.ACTIVE);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 40L)).thenReturn(Optional.of(manager));
        when(membershipRepository.findActiveManagersForUpdate(40L))
                .thenReturn(List.of(manager));

        assertThatThrownBy(() -> service.leave(1L, 40L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void managerCanLeaveWhenAnotherActiveManagerExists() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Membership manager = membership(user(1L), 40L, MembershipRole.MANAGER, MembershipStatus.ACTIVE);
        Membership manager2 = membership(user(2L), 40L, MembershipRole.MANAGER, MembershipStatus.ACTIVE);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 40L)).thenReturn(Optional.of(manager));
        when(membershipRepository.findActiveManagersForUpdate(40L))
                .thenReturn(List.of(manager, manager2));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipResponse resp = service.leave(1L, 40L);

        assertThat(resp.status()).isEqualTo(MembershipStatus.LEFT);
        assertThat(resp.role()).isEqualTo(MembershipRole.MANAGER);
    }

    @Test
    void leaveNonActiveThrows() {
        MembershipService service = new MembershipService(membershipRepository, communityRepository);
        Membership pending = membership(user(1L), 40L, MembershipRole.MEMBER, MembershipStatus.PENDING);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 40L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.leave(1L, 40L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}