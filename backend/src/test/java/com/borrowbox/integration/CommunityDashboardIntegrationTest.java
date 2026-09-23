package com.borrowbox.integration;

import com.borrowbox.dto.DashboardResponse;
import com.borrowbox.dto.RecentActivityDto;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.ReputationEvent;
import com.borrowbox.entity.ReputationEventType;
import com.borrowbox.entity.ReputationRole;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.ReputationEventRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityDashboardService;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.FlagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V2.4.2 dashboard roll-up against the real database. All rows are built in a
 * fresh community inside the test transaction, so every count is exact. The
 * dashboard aggregation must agree with the raw repository state line by line.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CommunityDashboardIntegrationTest {

    @Autowired
    private CommunityService communityService;
    @Autowired
    private CommunityDashboardService dashboardService;
    @Autowired
    private FlagService flagService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityRepository communityRepository;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private CommunityListingRepository communityListingRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private TransactionEventRepository transactionEventRepository;
    @Autowired
    private ReputationEventRepository reputationEventRepository;

    private record World(Community community, User manager, User lender, User borrower,
                         CommunityListing listing, Asset asset) {
    }

    private World world() {
        User manager = V242IntegrationFixture.newUser(userRepository, "DashMgr");
        User lender = V242IntegrationFixture.newUser(userRepository, "DashLender");
        User borrower = V242IntegrationFixture.newUser(userRepository, "DashBorrower");
        Community community = V242IntegrationFixture.newCommunity(communityService, communityRepository, manager);
        V242IntegrationFixture.joinActive(membershipRepository, lender, community, MembershipRole.MEMBER);
        V242IntegrationFixture.joinActive(membershipRepository, borrower, community, MembershipRole.MEMBER);
        V242IntegrationFixture.joinPending(membershipRepository,
                V242IntegrationFixture.newUser(userRepository, "DashPending"), community);
        Asset asset = V242IntegrationFixture.newAsset(assetRepository, lender, "Dashboard Binoculars");
        CommunityListing listing = V242IntegrationFixture.newListing(communityListingRepository, asset, community);
        return new World(community, manager, lender, borrower, listing, asset);
    }

    private ReputationEvent reputation(User user, Community community, Transaction txn,
                                       ReputationEventType type, ReputationRole role,
                                       LocalDateTime occurredAt) {
        ReputationEvent event = new ReputationEvent();
        event.setUser(user);
        event.setCommunity(community);
        event.setTransaction(txn);
        event.setEventType(type);
        event.setRole(role);
        event.setSuccessful(type == ReputationEventType.LOAN_COMPLETED);
        event.setOnTime(type == ReputationEventType.LOAN_COMPLETED ? null : false);
        event.setOccurredAt(occurredAt);
        return reputationEventRepository.save(event);
    }

    private TransactionEvent transactionEvent(Transaction txn, User actor, TransactionEventType type) {
        TransactionEvent event = new TransactionEvent();
        event.setTransaction(txn);
        event.setActor(actor);
        event.setEventType(type);
        return transactionEventRepository.save(event);
    }

    @Test
    void dashboardRollsUpEveryCommunityMetricExactly() {
        World w = world();
        LocalDateTime now = LocalDateTime.now();

        // Active loans: ACTIVE (t1 on time, t2 overdue), RETURN_INITIATED, RETURN_REPORTED.
        Transaction t1 = V242IntegrationFixture.newTransaction(transactionRepository, w.community(), w.listing(), w.asset(),
                w.borrower(), w.lender(), TransactionStatus.ACTIVE, now.plusDays(5), null);
        Transaction t2 = V242IntegrationFixture.newTransaction(transactionRepository, w.community(), w.listing(), w.asset(),
                w.borrower(), w.lender(), TransactionStatus.ACTIVE, now.minusDays(1), null);
        Transaction t3 = V242IntegrationFixture.newTransaction(transactionRepository, w.community(), w.listing(), w.asset(),
                w.borrower(), w.lender(), TransactionStatus.RETURN_INITIATED, now.plusDays(1), null);
        Transaction t4 = V242IntegrationFixture.newTransaction(transactionRepository, w.community(), w.listing(), w.asset(),
                w.borrower(), w.lender(), TransactionStatus.RETURN_REPORTED, now.plusDays(2), null);
        // Completed loans: t5 on time, t6 late.
        Transaction t5 = V242IntegrationFixture.newTransaction(transactionRepository, w.community(), w.listing(), w.asset(),
                w.borrower(), w.lender(), TransactionStatus.COMPLETED, now.minusDays(3), now.minusDays(4));
        Transaction t6 = V242IntegrationFixture.newTransaction(transactionRepository, w.community(), w.listing(), w.asset(),
                w.borrower(), w.lender(), TransactionStatus.COMPLETED, now.minusDays(6), now.minusDays(4));
        // Disputed terminals feed the dispute rate.
        Transaction t7 = V242IntegrationFixture.newTransaction(transactionRepository, w.community(), w.listing(), w.asset(),
                w.borrower(), w.lender(), TransactionStatus.HANDOVER_DISPUTED, now.minusDays(4), now.minusDays(4));
        Transaction t8 = V242IntegrationFixture.newTransaction(transactionRepository, w.community(), w.listing(), w.asset(),
                w.borrower(), w.lender(), TransactionStatus.RETURN_DISPUTED, now.minusDays(9), now.minusDays(9));

        reputation(w.borrower(), w.community(), t8, ReputationEventType.RETURN_DISPUTED,
                ReputationRole.BORROWER, now.minusMinutes(10));
        reputation(w.lender(), w.community(), t5, ReputationEventType.LOAN_COMPLETED,
                ReputationRole.LENDER, now.minusMinutes(20));
        transactionEvent(t1, w.manager(), TransactionEventType.LOAN_COMPLETED);

        flagService.createFlag(w.community().getId(), null, FlagType.MANUAL, "needs triage", w.manager());

        DashboardResponse dashboard = dashboardService.getDashboard(w.community().getId(), w.manager());

        assertThat(dashboard.communityId()).isEqualTo(w.community().getId());
        assertThat(dashboard.activeLoanCount()).isEqualTo(4);
        assertThat(dashboard.overdueLoanCount()).isEqualTo(1);
        assertThat(dashboard.pendingMembershipCount()).isEqualTo(1);
        assertThat(dashboard.activeMemberCount()).isEqualTo(3);
        assertThat(dashboard.openFlagCount()).isEqualTo(1);
        assertThat(dashboard.completedLoansCount()).isEqualTo(2);
        assertThat(dashboard.onTimeReturns()).isEqualTo(1);
        assertThat(dashboard.lateReturns()).isEqualTo(1);
        assertThat(dashboard.onTimeReturnRate()).isEqualTo(50);
        assertThat(dashboard.returnDisputesCount()).isEqualTo(1);
        assertThat(dashboard.completedLendsCount()).isEqualTo(1);
        assertThat(dashboard.returnDisputesReceivedCount()).isEqualTo(1);
        assertThat(dashboard.disputeRate()).isEqualTo(100);
        assertThat(dashboard.transactionVolume30d()).isEqualTo(8);

        // Recent activity merges transaction events and reputation events,
        // newest first, bounded at the dashboard limit.
        assertThat(dashboard.recentActivity()).hasSize(3);
        assertThat(dashboard.recentActivity()).extracting(RecentActivityDto::source)
                .containsExactly("transaction", "reputation", "reputation");
        assertThat(dashboard.recentActivity()).extracting(RecentActivityDto::eventType)
                .containsExactly("LOAN_COMPLETED", "RETURN_DISPUTED", "LOAN_COMPLETED");

        // Recent flags exposes the single open flag, newest first.
        assertThat(dashboard.recentFlags()).hasSize(1);
        assertThat(dashboard.recentFlags().get(0).flagType()).isEqualTo(FlagType.MANUAL);
        assertThat(dashboard.recentFlags().get(0).status().name()).isEqualTo("OPEN");
    }

    @Test
    void emptyCommunityDashboardUsesNullRates() {
        World w = world();

        DashboardResponse dashboard = dashboardService.getDashboard(w.community().getId(), w.manager());

        assertThat(dashboard.completedLoansCount()).isZero();
        assertThat(dashboard.onTimeReturnRate()).isNull();
        assertThat(dashboard.disputeRate()).isNull();
        assertThat(dashboard.recentActivity()).isEmpty();
        assertThat(dashboard.recentFlags()).isEmpty();
    }

    @Test
    void nonManagerCannotReadDashboard() {
        World w = world();
        User outsider = V242IntegrationFixture.newUser(userRepository, "DashOutsider");

        assertThatThrownBy(() -> dashboardService.getDashboard(w.community().getId(), outsider))
                .isInstanceOf(UnauthorizedException.class);
    }
}