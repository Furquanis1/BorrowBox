package com.borrowbox.service;

import com.borrowbox.dto.DashboardResponse;
import com.borrowbox.dto.HealthResponse;
import com.borrowbox.dto.RecentActivityDto;
import com.borrowbox.entity.Flag;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.ReputationEvent;
import com.borrowbox.entity.ReputationEventType;
import com.borrowbox.entity.ReputationRole;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.FlagRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.ReputationEventRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CommunityDashboardServiceTest {

    @Mock
    private MembershipService membershipService;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private FlagRepository flagRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private TransactionEventRepository transactionEventRepository;

    @Mock
    private ReputationEventRepository reputationEventRepository;

    private CommunityDashboardService service() {
        return new CommunityDashboardService(membershipService, membershipRepository,
                flagRepository, transactionRepository, transactionService,
                transactionEventRepository, reputationEventRepository);
    }

    private User manager() {
        User u = new User("Ahmed", "ahmed@example.com");
        u.setId(100L);
        return u;
    }

    @Test
    void requiresActiveManager() {
        when(membershipService.isActiveManager(100L, 900L)).thenReturn(false);

        assertThatThrownBy(() -> service().getDashboard(900L, manager()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service().getHealth(900L, manager()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void dashboardAggregatesLateAndOnTimeRates() {
        when(membershipService.isActiveManager(100L, 900L)).thenReturn(true);
        when(transactionRepository.countByCommunityIdAndStateIn(any(), any())).thenReturn(5L);
        when(transactionRepository.findByCommunityIdAndState(900L, TransactionStatus.ACTIVE))
                .thenReturn(List.of());
        when(membershipRepository.countByCommunityIdAndStatus(900L, MembershipStatus.PENDING)).thenReturn(2L);
        when(membershipRepository.countByCommunityIdAndStatus(900L, MembershipStatus.ACTIVE)).thenReturn(10L);
        when(flagRepository.countByCommunityIdAndStatus(900L, FlagStatus.OPEN)).thenReturn(3L);
        when(transactionRepository.countByCommunityIdAndStateAndDueAtIsNotNull(900L, TransactionStatus.COMPLETED))
                .thenReturn(4L);
        when(transactionRepository.countCompletedOnTime(900L, TransactionStatus.COMPLETED)).thenReturn(3L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeAndRole(
                900L, ReputationEventType.RETURN_DISPUTED, ReputationRole.BORROWER)).thenReturn(1L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeAndRole(
                900L, ReputationEventType.LOAN_COMPLETED, ReputationRole.LENDER)).thenReturn(4L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeWhereTransactionLenderIsMember(
                900L, ReputationEventType.RETURN_DISPUTED)).thenReturn(1L);
        when(transactionRepository.countByCommunityIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(7L);
        when(transactionEventRepository.findRecentByCommunityId(any(), any())).thenReturn(List.of());
        when(reputationEventRepository.findTop20ByCommunityIdOrderByOccurredAtDesc(900L))
                .thenReturn(List.of());
        when(flagRepository.findTop10ByCommunityIdOrderByOccurredAtDescIdDesc(900L)).thenReturn(List.of());

        DashboardResponse dashboard = service().getDashboard(900L, manager());

        assertThat(dashboard.activeLoanCount()).isEqualTo(5);
        assertThat(dashboard.overdueLoanCount()).isZero();
        assertThat(dashboard.pendingMembershipCount()).isEqualTo(2);
        assertThat(dashboard.activeMemberCount()).isEqualTo(10);
        assertThat(dashboard.openFlagCount()).isEqualTo(3);
        assertThat(dashboard.completedLoansCount()).isEqualTo(4);
        assertThat(dashboard.onTimeReturns()).isEqualTo(3);
        assertThat(dashboard.lateReturns()).isEqualTo(1);
        assertThat(dashboard.onTimeReturnRate()).isEqualTo(75);
        assertThat(dashboard.returnDisputesCount()).isEqualTo(1);
        assertThat(dashboard.completedLendsCount()).isEqualTo(4);
        assertThat(dashboard.returnDisputesReceivedCount()).isEqualTo(1);
        assertThat(dashboard.disputeRate()).isEqualTo(125);
        assertThat(dashboard.transactionVolume30d()).isEqualTo(7);
    }

    @Test
    void ratesAreNullWhenCompletedLoansZero() {
        when(membershipService.isActiveManager(100L, 900L)).thenReturn(true);
        when(transactionRepository.countByCommunityIdAndStateIn(any(), any())).thenReturn(0L);
        when(transactionRepository.findByCommunityIdAndState(900L, TransactionStatus.ACTIVE))
                .thenReturn(List.of());
        when(membershipRepository.countByCommunityIdAndStatus(any(), any())).thenReturn(0L);
        when(flagRepository.countByCommunityIdAndStatus(any(), any())).thenReturn(0L);
        when(transactionRepository.countByCommunityIdAndStateAndDueAtIsNotNull(900L, TransactionStatus.COMPLETED))
                .thenReturn(0L);
        when(transactionRepository.countCompletedOnTime(900L, TransactionStatus.COMPLETED)).thenReturn(0L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeAndRole(any(), any(), any())).thenReturn(0L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeWhereTransactionLenderIsMember(
                any(), any())).thenReturn(0L);
        when(transactionRepository.countByCommunityIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(0L);
        when(transactionEventRepository.findRecentByCommunityId(any(), any())).thenReturn(List.of());
        when(reputationEventRepository.findTop20ByCommunityIdOrderByOccurredAtDesc(any())).thenReturn(List.of());
        when(flagRepository.findTop10ByCommunityIdOrderByOccurredAtDescIdDesc(any())).thenReturn(List.of());

        DashboardResponse dashboard = service().getDashboard(900L, manager());

        assertThat(dashboard.onTimeReturnRate()).isNull();
        assertThat(dashboard.disputeRate()).isNull();
    }

    @Test
    void overdueCountReusesLoanClockIsOverdue() {
        when(membershipService.isActiveManager(100L, 900L)).thenReturn(true);
        when(transactionRepository.countByCommunityIdAndStateIn(any(), any())).thenReturn(2L);
        Transaction notOverdue = transaction(1L, TransactionStatus.ACTIVE);
        when(transactionRepository.findByCommunityIdAndState(900L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(notOverdue));
        when(transactionService.isOverdue(notOverdue)).thenReturn(true);
        when(membershipRepository.countByCommunityIdAndStatus(any(), any())).thenReturn(0L);
        when(flagRepository.countByCommunityIdAndStatus(any(), any())).thenReturn(0L);
        when(transactionRepository.countByCommunityIdAndStateAndDueAtIsNotNull(900L, TransactionStatus.COMPLETED))
                .thenReturn(0L);
        when(transactionRepository.countCompletedOnTime(900L, TransactionStatus.COMPLETED)).thenReturn(0L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeAndRole(any(), any(), any())).thenReturn(0L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeWhereTransactionLenderIsMember(
                any(), any())).thenReturn(0L);
        when(transactionRepository.countByCommunityIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(0L);
        when(transactionEventRepository.findRecentByCommunityId(any(), any())).thenReturn(List.of());
        when(reputationEventRepository.findTop20ByCommunityIdOrderByOccurredAtDesc(any())).thenReturn(List.of());
        when(flagRepository.findTop10ByCommunityIdOrderByOccurredAtDescIdDesc(any())).thenReturn(List.of());

        DashboardResponse dashboard = service().getDashboard(900L, manager());

        assertThat(dashboard.overdueLoanCount()).isEqualTo(1);
    }

    @Test
    void recentActivityMergesBothSourcesSortedNewestFirst() {
        when(membershipService.isActiveManager(100L, 900L)).thenReturn(true);
        when(transactionRepository.countByCommunityIdAndStateIn(any(), any())).thenReturn(0L);
        when(transactionRepository.findByCommunityIdAndState(900L, TransactionStatus.ACTIVE))
                .thenReturn(List.of());
        when(membershipRepository.countByCommunityIdAndStatus(any(), any())).thenReturn(0L);
        when(flagRepository.countByCommunityIdAndStatus(any(), any())).thenReturn(0L);
        when(transactionRepository.countByCommunityIdAndStateAndDueAtIsNotNull(900L, TransactionStatus.COMPLETED))
                .thenReturn(0L);
        when(transactionRepository.countCompletedOnTime(900L, TransactionStatus.COMPLETED)).thenReturn(0L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeAndRole(any(), any(), any())).thenReturn(0L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeWhereTransactionLenderIsMember(
                any(), any())).thenReturn(0L);
        when(transactionRepository.countByCommunityIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(0L);

        Transaction t = transaction(1L, TransactionStatus.COMPLETED);
        TransactionEvent old = new TransactionEvent();
        old.setId(1L);
        old.setEventType(TransactionEventType.LOAN_COMPLETED);
        old.setTransaction(t);
        old.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        User actor = new User("Act", "act@example.com");
        actor.setId(7L);
        old.setActor(actor);

        ReputationEvent newest = new ReputationEvent();
        newest.setId(2L);
        newest.setEventType(ReputationEventType.RETURN_DISPUTED);
        newest.setTransaction(t);
        newest.setUser(actor);
        newest.setOccurredAt(LocalDateTime.of(2026, 1, 1, 12, 0));

        when(transactionEventRepository.findRecentByCommunityId(any(), any())).thenReturn(List.of(old));
        when(reputationEventRepository.findTop20ByCommunityIdOrderByOccurredAtDesc(900L))
                .thenReturn(List.of(newest));
        when(flagRepository.findTop10ByCommunityIdOrderByOccurredAtDescIdDesc(any())).thenReturn(List.of());

        DashboardResponse dashboard = service().getDashboard(900L, manager());

        assertThat(dashboard.recentActivity()).hasSize(2);
        assertThat(dashboard.recentActivity().get(0).occurredAt())
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0));
        assertThat(dashboard.recentActivity().get(0).source()).isEqualTo("reputation");
        assertThat(dashboard.recentActivity().get(1).source()).isEqualTo("transaction");
        assertThat(dashboard.recentActivity().get(0).eventType()).isEqualTo("RETURN_DISPUTED");
    }

    @Test
    void healthCardExposesDashboardSubset() {
        when(membershipService.isActiveManager(100L, 900L)).thenReturn(true);
        when(transactionRepository.countByCommunityIdAndStateIn(any(), any())).thenReturn(0L);
        when(transactionRepository.findByCommunityIdAndState(900L, TransactionStatus.ACTIVE))
                .thenReturn(List.of());
        when(membershipRepository.countByCommunityIdAndStatus(900L, MembershipStatus.ACTIVE)).thenReturn(6L);
        when(membershipRepository.countByCommunityIdAndStatus(900L, MembershipStatus.PENDING)).thenReturn(2L);
        when(flagRepository.countByCommunityIdAndStatus(900L, FlagStatus.OPEN)).thenReturn(2L);
        when(transactionRepository.countByCommunityIdAndStateAndDueAtIsNotNull(900L, TransactionStatus.COMPLETED))
                .thenReturn(4L);
        when(transactionRepository.countCompletedOnTime(900L, TransactionStatus.COMPLETED)).thenReturn(2L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeAndRole(
                900L, ReputationEventType.RETURN_DISPUTED, ReputationRole.BORROWER)).thenReturn(1L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeAndRole(
                900L, ReputationEventType.LOAN_COMPLETED, ReputationRole.LENDER)).thenReturn(4L);
        when(reputationEventRepository.countByCommunityIdAndEventTypeWhereTransactionLenderIsMember(
                900L, ReputationEventType.RETURN_DISPUTED)).thenReturn(1L);

        HealthResponse health = service().getHealth(900L, manager());

        assertThat(health.activeMemberCount()).isEqualTo(6);
        assertThat(health.openFlagCount()).isEqualTo(2);
        assertThat(health.completedLoansCount()).isEqualTo(4);
        assertThat(health.onTimeReturns()).isEqualTo(2);
        assertThat(health.onTimeReturnRate()).isEqualTo(50);
        assertThat(health.returnDisputesCount()).isEqualTo(1);
        assertThat(health.completedLendsCount()).isEqualTo(4);
        assertThat(health.returnDisputesReceivedCount()).isEqualTo(1);
    }

    private Transaction transaction(Long id, TransactionStatus state) {
        com.borrowbox.entity.Community c = new com.borrowbox.entity.Community();
        c.setId(900L);
        Transaction t = new Transaction();
        t.setId(id);
        t.setCommunity(c);
        t.setState(state);
        return t;
    }
}
