package com.borrowbox.service;

import com.borrowbox.entity.Community;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.User;
import com.borrowbox.repository.MembershipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MembershipServiceTest {

    @Mock
    private MembershipRepository membershipRepository;

    private User user(Long id) {
        User u = new User("U" + id, "u" + id + "@example.com");
        u.setId(id);
        return u;
    }

    private Membership membership(User user, Long communityId, MembershipRole role, MembershipStatus status) {
        Membership m = new Membership();
        m.setUser(user);
        Community c = new Community();
        c.setId(communityId);
        m.setCommunity(c);
        m.setRole(role);
        m.setStatus(status);
        return m;
    }

    @Test
    void isActiveMemberReturnsTrueForActiveMembership() {
        MembershipService service = new MembershipService(membershipRepository);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 2L))
                .thenReturn(Optional.of(membership(user(1L), 2L, MembershipRole.MEMBER, MembershipStatus.ACTIVE)));

        assertThat(service.isActiveMember(1L, 2L)).isTrue();
    }

    @Test
    void isActiveMemberReturnsFalseForNonActive() {
        MembershipService service = new MembershipService(membershipRepository);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 2L))
                .thenReturn(Optional.of(membership(user(1L), 2L, MembershipRole.MEMBER, MembershipStatus.LEFT)));

        assertThat(service.isActiveMember(1L, 2L)).isFalse();
    }

    @Test
    void isActiveManagerReturnsTrueOnlyForActiveManager() {
        MembershipService service = new MembershipService(membershipRepository);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 2L))
                .thenReturn(Optional.of(membership(user(1L), 2L, MembershipRole.MANAGER, MembershipStatus.ACTIVE)));

        assertThat(service.isActiveManager(1L, 2L)).isTrue();
    }

    @Test
    void isActiveManagerReturnsFalseForActiveMember() {
        MembershipService service = new MembershipService(membershipRepository);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 2L))
                .thenReturn(Optional.of(membership(user(1L), 2L, MembershipRole.MEMBER, MembershipStatus.ACTIVE)));

        assertThat(service.isActiveManager(1L, 2L)).isFalse();
    }

    @Test
    void isActiveManagerReturnsFalseWhenMembershipAbsent() {
        MembershipService service = new MembershipService(membershipRepository);
        when(membershipRepository.findByUserIdAndCommunityId(1L, 2L))
                .thenReturn(Optional.empty());

        assertThat(service.isActiveManager(1L, 2L)).isFalse();
        assertThat(service.isActiveMember(1L, 2L)).isFalse();
    }
}
