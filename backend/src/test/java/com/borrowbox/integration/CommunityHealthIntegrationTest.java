package com.borrowbox.integration;

import com.borrowbox.dto.HealthResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.ReputationEvent;
import com.borrowbox.entity.ReputationEventType;
import com.borrowbox.entity.ReputationRole;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.ReputationEventRepository;
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
 * V2.4.2 community health card is the same aggregation as the dashboard roll-up:
 * the values must match the raw database state and the shared fields of the
 * health card must equal the dashboard's, while every read stays manager-only.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CommunityHealthIntegrationTest {

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
    private ReputationEventRepository reputationEventRepository;

    private record World(Community community, User manager, User member, User pending,
                         CommunityListing listing, Asset asset) {
    }

    private World world() {
        User manager = V242IntegrationFixture.newUser(userRepository, "HealthMgr");
        User member = V242IntegrationFixture.newUser(userRepository, "HealthMember");
        User pending = V242IntegrationFixture.newUser(userRepository, "HealthPending");
        Community community = V242IntegrationFixture.newCommunity(communityService, communityRepository, manager);
        V242IntegrationFixture.joinActive(membershipRepository, member, community, MembershipRole.MEMBER);
        V242IntegrationFixture.joinPending(membershipRepository, pending, community);
        Asset asset = V242IntegrationFixture.newAsset(assetRepository, member, "Health Toolkit");
        CommunityListing listing = V242IntegrationFixture.newListing(communityListingRepository, asset, community);
        return new World(community, manager, member, pending, listing, asset);
    }

    @Test
    void healthCardAggregatesAndMatchesDashboard() {
        World w = world();
        LocalDateTime now = LocalDateTime.now();

        Transaction overdueActive = V242IntegrationFixture.newTransaction(transactionRepository,
                w.community(), w.listing(), w.asset(), w.member(), w.manager(),
                TransactionStatus.ACTIVE, now.minusDays(1), null);
        Transaction onTime = V242IntegrationFixture.newTransaction(transactionRepository,
                w.community(), w.listing(), w.asset(), w.member(), w.manager(),
                TransactionStatus.COMPLETED, now.minusDays(3), now.minusDays(4));
        Transaction late = V242IntegrationFixture.newTransaction(transactionRepository,
                w.community(), w.listing(), w.asset(), w.member(), w.manager(),
                TransactionStatus.COMPLETED, now.minusDays(6), now.minusDays(4));
        Transaction disputed = V242IntegrationFixture.newTransaction(transactionRepository,
                w.community(), w.listing(), w.asset(), w.member(), w.manager(),
                TransactionStatus.HANDOVER_DISPUTED, now.minusDays(4), now.minusDays(4));

        reputation(w.member(), w.community(), disputed, ReputationEventType.RETURN_DISPUTED,
                ReputationRole.BORROWER, now.minusMinutes(5));
        reputation(w.manager(), w.community(), onTime, ReputationEventType.LOAN_COMPLETED,
                ReputationRole.LENDER, now.minusMinutes(10));
        flagService.createFlag(w.community().getId(), null, FlagType.EVIDENCE_ISSUE,
                "photos missing", w.manager());

        HealthResponse health = dashboardService.getHealth(w.community().getId(), w.manager());

        assertThat(health.communityId()).isEqualTo(w.community().getId());
        assertThat(health.activeMemberCount()).isEqualTo(2);
        assertThat(health.openFlagCount()).isEqualTo(1);
        assertThat(health.overdueLoanCount()).isEqualTo(1);
        assertThat(health.completedLoansCount()).isEqualTo(2);
        assertThat(health.onTimeReturns()).isEqualTo(1);
        assertThat(health.onTimeReturnRate()).isEqualTo(50);
        assertThat(health.returnDisputesCount()).isEqualTo(1);
        assertThat(health.completedLendsCount()).isEqualTo(1);
        assertThat(health.returnDisputesReceivedCount()).isEqualTo(1);
        assertThat(health.disputeRate()).isEqualTo(50);

        // The health card is a strict subset of the dashboard roll-up.
        var dashboard = dashboardService.getDashboard(w.community().getId(), w.manager());
        assertThat(dashboard.activeMemberCount()).isEqualTo(health.activeMemberCount());
        assertThat(dashboard.openFlagCount()).isEqualTo(health.openFlagCount());
        assertThat(dashboard.overdueLoanCount()).isEqualTo(health.overdueLoanCount());
        assertThat(dashboard.completedLoansCount()).isEqualTo(health.completedLoansCount());
        assertThat(dashboard.onTimeReturns()).isEqualTo(health.onTimeReturns());
        assertThat(dashboard.onTimeReturnRate()).isEqualTo(health.onTimeReturnRate());
        assertThat(dashboard.returnDisputesCount()).isEqualTo(health.returnDisputesCount());
        assertThat(dashboard.completedLendsCount()).isEqualTo(health.completedLendsCount());
        assertThat(dashboard.returnDisputesReceivedCount()).isEqualTo(health.returnDisputesReceivedCount());
        assertThat(dashboard.disputeRate()).isEqualTo(health.disputeRate());
    }

    @Test
    void healthCardIsManagerOnly() {
        World w = world();

        assertThatThrownBy(() -> dashboardService.getHealth(w.community().getId(), w.member()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> dashboardService.getHealth(w.community().getId(), w.pending()))
                .isInstanceOf(UnauthorizedException.class);

        User outsider = V242IntegrationFixture.newUser(userRepository, "HealthOutsider");
        assertThatThrownBy(() -> dashboardService.getHealth(w.community().getId(), outsider))
                .isInstanceOf(UnauthorizedException.class);
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
}