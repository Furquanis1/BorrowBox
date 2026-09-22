package com.borrowbox.service;

import com.borrowbox.dto.TrustProfileResponse;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.ReputationEventType;
import com.borrowbox.entity.ReputationRole;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.ReputationEventRepository;
import com.borrowbox.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TrustProfileServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 9, 0, 0);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private ReputationEventRepository reputationEventRepository;

    private TrustProfileService service;

    @BeforeEach
    void setUp() {
        service = new TrustProfileService(transactionRepository, membershipRepository, reputationEventRepository);
        // Default: no reputation events (lenient to avoid strict stubbing issues)
        lenient().when(reputationEventRepository.countByUserIdAndRoleAndEventType(anyLong(), any(), any(), anyLong()))
                .thenReturn(0L);
        lenient().when(reputationEventRepository.countByTransactionLenderIdAndEventType(anyLong(), any(), anyLong()))
                .thenReturn(0L);
    }

    private Transaction txn(Long id, TransactionStatus state) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setState(state);
        return t;
    }

    private Transaction completed(Long id, LocalDateTime dueAt, LocalDateTime completedAt) {
        Transaction t = txn(id, TransactionStatus.COMPLETED);
        t.setOriginalDueAt(dueAt);
        t.setDueAt(dueAt);
        t.setCompletedAt(completedAt);
        return t;
    }

    private Community community(Long id, String name) {
        Community c = new Community();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private Membership activeMembership(Long userId, Long communityId, String communityName) {
        Membership m = new Membership();
        m.setStatus(MembershipStatus.ACTIVE);
        m.setCommunity(community(communityId, communityName));
        return m;
    }

    // ─── Global metrics ─────────────────────────────────────────────────────

    @Test
    void globalProfileDerivesMetricsAcrossBothRoles() {
        Transaction borrowed = completed(1L, BASE, BASE.minusDays(1));
        Transaction lent = completed(2L, BASE, BASE);
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L)).thenReturn(List.of(borrowed));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L)).thenReturn(List.of(lent));

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.communityId()).isNull();
        assertThat(resp.communityName()).isNull();
        assertThat(resp.itemsBorrowed()).isEqualTo(1);
        assertThat(resp.itemsLent()).isEqualTo(1);
        assertThat(resp.successfulTransactions()).isEqualTo(2);
        assertThat(resp.completedLoans()).isEqualTo(1);
        assertThat(resp.onTimeReturns()).isEqualTo(1);
        assertThat(resp.lateReturns()).isZero();
        assertThat(resp.onTimeReturnRate()).isEqualTo(1.0);
        verify(transactionRepository, never()).findByBorrowerIdAndCommunityIdOrderByIdDesc(any(), any());
    }

    @Test
    void globalProfileReturnsNullRateWhenNoCompletedLoans() {
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L))
                .thenReturn(List.of(txn(1L, TransactionStatus.ACTIVE)));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L))
                .thenReturn(List.of(txn(2L, TransactionStatus.RETURN_INITIATED)));

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.itemsBorrowed()).isEqualTo(1);
        assertThat(resp.itemsLent()).isEqualTo(1);
        assertThat(resp.successfulTransactions()).isZero();
        assertThat(resp.completedLoans()).isZero();
        assertThat(resp.onTimeReturnRate()).isNull();
    }

    @Test
    void activeBorrowedCountsAsItemButNotSuccessful() {
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L))
                .thenReturn(List.of(txn(1L, TransactionStatus.ACTIVE)));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L)).thenReturn(List.of());

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.itemsBorrowed()).isEqualTo(1);
        assertThat(resp.successfulTransactions()).isZero();
        assertThat(resp.completedLoans()).isZero();
    }

    @Test
    void returnInitiatedAndReturnReportedCountAsBorrowedItems() {
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L)).thenReturn(List.of(
                txn(1L, TransactionStatus.RETURN_INITIATED),
                txn(2L, TransactionStatus.RETURN_REPORTED)));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L)).thenReturn(List.of());

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.itemsBorrowed()).isEqualTo(2);
        assertThat(resp.successfulTransactions()).isZero();
        assertThat(resp.completedLoans()).isZero();
    }

    @Test
    void completedCountsAsBorrowedSuccessfulAndDenominator() {
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L))
                .thenReturn(List.of(completed(1L, BASE, BASE.minusDays(1))));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L)).thenReturn(List.of());

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.itemsBorrowed()).isEqualTo(1);
        assertThat(resp.successfulTransactions()).isEqualTo(1);
        assertThat(resp.completedLoans()).isEqualTo(1);
        assertThat(resp.onTimeReturns()).isEqualTo(1);
        assertThat(resp.lateReturns()).isZero();
        assertThat(resp.onTimeReturnRate()).isEqualTo(1.0);
    }

    @Test
    void lateCompletionDerivesLateMetrics() {
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L))
                .thenReturn(List.of(completed(1L, BASE, BASE.plusDays(1))));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L)).thenReturn(List.of());

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.completedLoans()).isEqualTo(1);
        assertThat(resp.onTimeReturns()).isZero();
        assertThat(resp.lateReturns()).isEqualTo(1);
        assertThat(resp.onTimeReturnRate()).isEqualTo(0.0);
    }

    @Test
    void completedWithoutDueAtExcludesDenominatorButKeepsSuccess() {
        Transaction t = txn(1L, TransactionStatus.COMPLETED);
        t.setCompletedAt(BASE);
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L)).thenReturn(List.of(t));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L)).thenReturn(List.of());

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.itemsBorrowed()).isEqualTo(1);
        assertThat(resp.successfulTransactions()).isEqualTo(1);
        assertThat(resp.completedLoans()).isZero();
        assertThat(resp.onTimeReturnRate()).isNull();
    }

    @Test
    void returnDisputedCountsAsBorrowedButNotSuccessful() {
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L))
                .thenReturn(List.of(txn(1L, TransactionStatus.RETURN_DISPUTED)));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L)).thenReturn(List.of());

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.itemsBorrowed()).isEqualTo(1);
        assertThat(resp.successfulTransactions()).isZero();
        assertThat(resp.completedLoans()).isZero();
        assertThat(resp.onTimeReturnRate()).isNull();
    }

    @Test
    void handoverDisputedExcludedFromEveryMetric() {
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L))
                .thenReturn(List.of(txn(1L, TransactionStatus.HANDOVER_DISPUTED)));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L))
                .thenReturn(List.of(txn(2L, TransactionStatus.HANDOVER_DISPUTED)));

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.itemsBorrowed()).isZero();
        assertThat(resp.itemsLent()).isZero();
        assertThat(resp.successfulTransactions()).isZero();
        assertThat(resp.completedLoans()).isZero();
        assertThat(resp.onTimeReturnRate()).isNull();
    }

    @Test
    void rejectedCancelledApprovedAndAwaitingHandoverAreAllExcluded() {
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L)).thenReturn(List.of(
                txn(1L, TransactionStatus.PENDING),
                txn(2L, TransactionStatus.APPROVED),
                txn(3L, TransactionStatus.REJECTED),
                txn(4L, TransactionStatus.CANCELLED),
                txn(5L, TransactionStatus.COUNTER_OFFERED),
                txn(6L, TransactionStatus.AWAITING_HANDOVER)));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L))
                .thenReturn(List.of(txn(7L, TransactionStatus.REJECTED)));

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.itemsBorrowed()).isZero();
        assertThat(resp.itemsLent()).isZero();
        assertThat(resp.successfulTransactions()).isZero();
        assertThat(resp.completedLoans()).isZero();
        assertThat(resp.onTimeReturnRate()).isNull();
    }

    @Test
    void successfulTransactionsIsUnionAcrossBothRoles() {
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L))
                .thenReturn(List.of(completed(1L, BASE, BASE)));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L))
                .thenReturn(List.of(completed(2L, BASE, BASE.minusHours(1))));

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.successfulTransactions()).isEqualTo(2);
    }

    @Test
    void onTimeUsesFinalDueAtAfterExtension() {
        Transaction t = txn(1L, TransactionStatus.COMPLETED);
        t.setOriginalDueAt(BASE.minusDays(2));
        t.setDueAt(BASE);
        t.setCompletedAt(BASE.minusHours(1));
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L)).thenReturn(List.of(t));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L)).thenReturn(List.of());

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.onTimeReturns()).isEqualTo(1);
        assertThat(resp.lateReturns()).isZero();
        assertThat(resp.onTimeReturnRate()).isEqualTo(1.0);
    }

    @Test
    void multiCommunityUserGlobalProfileSumsAcrossAllCommunities() {
        Community a = community(10L, "A");
        Community b = community(20L, "B");
        Transaction t1 = completed(1L, BASE, BASE);
        t1.setCommunity(a);
        Transaction t2 = completed(2L, BASE, BASE.plusDays(2));
        t2.setCommunity(b);
        when(transactionRepository.findByBorrowerIdOrderByIdDesc(7L)).thenReturn(List.of(t1, t2));
        when(transactionRepository.findByLenderIdOrderByIdDesc(7L)).thenReturn(List.of());

        TrustProfileResponse resp = service.getTrustProfile(7L, null);

        assertThat(resp.itemsBorrowed()).isEqualTo(2);
        assertThat(resp.successfulTransactions()).isEqualTo(2);
        assertThat(resp.onTimeReturns()).isEqualTo(1);
        assertThat(resp.lateReturns()).isEqualTo(1);
        assertThat(resp.onTimeReturnRate()).isEqualTo(0.5);
    }

    // ─── Community scope ────────────────────────────────────────────────────

    @Test
    void communityProfileRequiresActiveMembershipAndScopesMetrics() {
        when(membershipRepository.findByUserIdAndCommunityIdAndStatus(7L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(7L, 10L, "Engineering Office")));
        when(transactionRepository.findByBorrowerIdAndCommunityIdOrderByIdDesc(7L, 10L))
                .thenReturn(List.of(completed(1L, BASE, BASE)));
        when(transactionRepository.findByLenderIdAndCommunityIdOrderByIdDesc(7L, 10L))
                .thenReturn(List.of(txn(2L, TransactionStatus.ACTIVE)));

        TrustProfileResponse resp = service.getTrustProfile(7L, 10L);

        assertThat(resp.communityId()).isEqualTo(10L);
        assertThat(resp.communityName()).isEqualTo("Engineering Office");
        assertThat(resp.itemsBorrowed()).isEqualTo(1);
        assertThat(resp.itemsLent()).isEqualTo(1);
        assertThat(resp.successfulTransactions()).isEqualTo(1);
        assertThat(resp.onTimeReturnRate()).isEqualTo(1.0);
        verify(transactionRepository, never()).findByBorrowerIdOrderByIdDesc(any());
        verify(transactionRepository, never()).findByLenderIdOrderByIdDesc(any());
    }

    @Test
    void communityProfileWithoutActiveMembershipDeniesAccess() {
        when(membershipRepository.findByUserIdAndCommunityIdAndStatus(7L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTrustProfile(7L, 10L))
                .isInstanceOf(AccessDeniedException.class);
        verify(transactionRepository, never()).findByBorrowerIdOrderByIdDesc(any());
        verify(transactionRepository, never()).findByBorrowerIdAndCommunityIdOrderByIdDesc(any(), any());
    }

    @Test
    void communityProfileFiltersToOnlyThatCommunityTransactions() {
        when(membershipRepository.findByUserIdAndCommunityIdAndStatus(7L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(7L, 10L, "Engineering Office")));
        when(transactionRepository.findByBorrowerIdAndCommunityIdOrderByIdDesc(7L, 10L))
                .thenReturn(List.of(completed(1L, BASE, BASE)));
        when(transactionRepository.findByLenderIdAndCommunityIdOrderByIdDesc(7L, 10L))
                .thenReturn(List.of());

        TrustProfileResponse resp = service.getTrustProfile(7L, 10L);

        assertThat(resp.itemsBorrowed()).isEqualTo(1);
        assertThat(resp.successfulTransactions()).isEqualTo(1);
        verify(transactionRepository).findByBorrowerIdAndCommunityIdOrderByIdDesc(eq(7L), eq(10L));
        verify(transactionRepository).findByLenderIdAndCommunityIdOrderByIdDesc(eq(7L), eq(10L));
    }

    @Test
    void communityProfileCrossCommunityFilteringKeepsScopedSetOnly() {
        when(membershipRepository.findByUserIdAndCommunityIdAndStatus(8L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(8L, 10L, "Engineering Office")));
        when(transactionRepository.findByBorrowerIdAndCommunityIdOrderByIdDesc(8L, 10L))
                .thenReturn(List.of(completed(1L, BASE, BASE)));
        when(transactionRepository.findByLenderIdAndCommunityIdOrderByIdDesc(8L, 10L))
                .thenReturn(List.of(txn(2L, TransactionStatus.HANDOVER_DISPUTED)));

        TrustProfileResponse resp = service.getTrustProfile(8L, 10L);

        assertThat(resp.itemsBorrowed()).isEqualTo(1);
        assertThat(resp.itemsLent()).isZero();
        assertThat(resp.successfulTransactions()).isEqualTo(1);
    }

    @Test
    void profileWithoutUserThrowsUnauthorized() {
        assertThatThrownBy(() -> service.getTrustProfile(null, null))
                .isInstanceOf(com.borrowbox.exception.UnauthorizedException.class);
    }
}