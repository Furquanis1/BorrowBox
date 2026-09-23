package com.borrowbox.integration;

import com.borrowbox.dto.CommunityRuleRequest;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.CommunityRuleType;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityDashboardService;
import com.borrowbox.service.CommunityRuleService;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V2.4.2 the dashboard's overdue count must interpret the loan clock exactly
 * like the authoritative {@link TransactionService#isOverdue}: an ACTIVE
 * transaction counts as overdue only once now is past dueAt plus the
 * community's active OVERDUE_GRACE_PERIOD. A missing rule keeps the legacy
 * zero-grace behaviour in both reads.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CommunityGracePeriodConsistencyIntegrationTest {

    @Autowired
    private CommunityService communityService;
    @Autowired
    private CommunityRuleService communityRuleService;
    @Autowired
    private CommunityDashboardService dashboardService;
    @Autowired
    private TransactionService transactionService;
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

    private record World(Community community, User manager, CommunityListing listing, Asset asset) {
    }

    private World world() {
        User manager = V242IntegrationFixture.newUser(userRepository, "GraceMgr");
        Community community = V242IntegrationFixture.newCommunity(communityService, communityRepository, manager);
        Asset asset = V242IntegrationFixture.newAsset(assetRepository, manager, "Grace Projector");
        CommunityListing listing = V242IntegrationFixture.newListing(communityListingRepository, asset, community);
        return new World(community, manager, listing, asset);
    }

    private Transaction activeLoan(World w, LocalDateTime dueAt) {
        User borrower = V242IntegrationFixture.newUser(userRepository, "GraceBorrower");
        return V242IntegrationFixture.newTransaction(transactionRepository, w.community(), w.listing(), w.asset(),
                borrower, w.manager(), TransactionStatus.ACTIVE, dueAt, null);
    }

    @Test
    void configuredGraceClampsOverdueCountConsistently() {
        World w = world();
        communityRuleService.createRule(w.community().getId(),
                new CommunityRuleRequest(CommunityRuleType.OVERDUE_GRACE_PERIOD, Map.of("days", 3)),
                w.manager());

        LocalDateTime now = LocalDateTime.now();
        Transaction pastGrace = activeLoan(w, now.minusDays(4));
        Transaction insideGrace = activeLoan(w, now.minusDays(2));
        Transaction future = activeLoan(w, now.plusDays(1));

        assertThat(transactionService.isOverdue(pastGrace)).isTrue();
        assertThat(transactionService.isOverdue(insideGrace)).isFalse();
        assertThat(transactionService.isOverdue(future)).isFalse();

        assertThat(dashboardService.getDashboard(w.community().getId(), w.manager()).overdueLoanCount())
                .isEqualTo(1);
    }

    @Test
    void missingRuleKeepsZeroGraceInBothReads() {
        World w = world();
        LocalDateTime now = LocalDateTime.now();
        Transaction overdue = activeLoan(w, now.minusHours(12));
        Transaction dueFuture = activeLoan(w, now.plusHours(12));

        assertThat(transactionService.isOverdue(overdue)).isTrue();
        assertThat(transactionService.isOverdue(dueFuture)).isFalse();
        assertThat(dashboardService.getDashboard(w.community().getId(), w.manager()).overdueLoanCount())
                .isEqualTo(1);
    }

    @Test
    void zeroGraceRuleIsEquivalentToNoRule() {
        World w = world();
        communityRuleService.createRule(w.community().getId(),
                new CommunityRuleRequest(CommunityRuleType.OVERDUE_GRACE_PERIOD, Map.of("days", 0)),
                w.manager());

        Transaction overdue = activeLoan(w, LocalDateTime.now().minusHours(12));
        assertThat(transactionService.isOverdue(overdue)).isTrue();
        assertThat(dashboardService.getDashboard(w.community().getId(), w.manager()).overdueLoanCount())
                .isEqualTo(1);
    }

    @Test
    void membershipsDoNotLeakIntoOverdueCount() {
        World w = world();
        User member = V242IntegrationFixture.newUser(userRepository, "GraceMember");
        V242IntegrationFixture.joinActive(membershipRepository, member, w.community(), MembershipRole.MEMBER);

        adminOnlyTransaction(w);

        assertThat(dashboardService.getDashboard(w.community().getId(), w.manager()).overdueLoanCount())
                .isEqualTo(0);
        assertThat(dashboardService.getDashboard(w.community().getId(), w.manager()).activeLoanCount())
                .isEqualTo(0);
    }

    private void adminOnlyTransaction(World w) {
        // A transaction in a different community state must never be visible here.
        User managerB = V242IntegrationFixture.newUser(userRepository, "GraceMgrB");
        Community communityB = V242IntegrationFixture.newCommunity(communityService, communityRepository, managerB);
        Asset assetB = V242IntegrationFixture.newAsset(assetRepository, managerB, "Grace Camera");
        CommunityListing listingB = V242IntegrationFixture.newListing(communityListingRepository, assetB, communityB);
        V242IntegrationFixture.newTransaction(transactionRepository, communityB, listingB, assetB,
                managerB, managerB, TransactionStatus.ACTIVE, LocalDateTime.now().minusDays(5), null);
    }
}