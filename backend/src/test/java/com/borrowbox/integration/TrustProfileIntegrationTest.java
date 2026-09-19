package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.TrustProfileResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.TrustProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the V2.3.1 derived trust profile against real MySQL.
 *
 * Isolated throwaway users and transactions are created inside the rolled-back
 * test transaction so the assertions are exact regardless of what other
 * committed rows already exist in the shared test database. The locked rules
 * verified here: the loan-state set, HANDOVER_DISPUTED exclusion, the on-time
 * denominator from the final dueAt, the cross-role successful-transaction
 * union, the null rate on an empty denominator, and community-scope
 * authorization (both missing and non-ACTIVE membership).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TrustProfileIntegrationTest {

    @Autowired private SeedDataInitializer seedDataInitializer;
    @Autowired private TrustProfileService trustProfileService;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private CommunityListingRepository communityListingRepository;
    @Autowired private TransactionRepository transactionRepository;

    private User isolatedUser(String label) {
        User user = new User(label, label.toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        user.setPasswordHash("$2a$10$integrationtestplaceholder");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private Community seedCommunity(String name) {
        return communityRepository.findAll().stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed community " + name));
    }

    private Membership join(User user, Community community, MembershipStatus status) {
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setCommunity(community);
        membership.setRole(MembershipRole.MEMBER);
        membership.setStatus(status);
        membership.setJoinedAt(LocalDateTime.now());
        return membershipRepository.save(membership);
    }

    private Transaction transaction(User borrower, User lender, Community community,
                                    CommunityListing listing, Asset asset,
                                    TransactionStatus state,
                                    LocalDateTime dueAt, LocalDateTime completedAt) {
        Transaction txn = new Transaction();
        txn.setCommunity(community);
        txn.setListing(listing);
        txn.setAsset(asset);
        txn.setBorrower(borrower);
        txn.setLender(lender);
        txn.setState(state);
        txn.setPurpose("trust profile integration fixture");
        txn.setRequestedDurationDays(1);
        if (dueAt != null) {
            txn.setStartedAt(dueAt.minusDays(1));
            txn.setDueAt(dueAt);
            txn.setOriginalDueAt(dueAt);
        }
        txn.setCompletedAt(completedAt);
        return transactionRepository.save(txn);
    }

    @Test
    void scopedBorrowerProfileAppliesEveryLockedRule() {
        seedDataInitializer.seed();
        User borrower = isolatedUser("Borrower");
        User lender = isolatedUser("Lender");
        Community office = seedCommunity("Engineering Office");
        join(borrower, office, MembershipStatus.ACTIVE);
        Asset football = assetRepository.findByOwnerId(
                        userRepository.findByEmail("ahmed@example.com").orElseThrow().getId()).stream()
                .filter(a -> a.getTitle().equals("Football"))
                .findFirst()
                .orElseThrow();
        CommunityListing listing = communityListingRepository
                .findByAssetIdAndCommunityId(football.getId(), office.getId())
                .orElseThrow();

        LocalDateTime dueA = LocalDateTime.of(2026, 6, 10, 9, 0);
        LocalDateTime dueB = LocalDateTime.of(2026, 6, 20, 9, 0);
        transaction(borrower, lender, office, listing, football,
                TransactionStatus.COMPLETED, dueA, dueA.minusHours(2));
        transaction(borrower, lender, office, listing, football,
                TransactionStatus.COMPLETED, dueB, dueB.plusDays(1));
        transaction(borrower, lender, office, listing, football,
                TransactionStatus.ACTIVE, null, null);
        transaction(borrower, lender, office, listing, football,
                TransactionStatus.HANDOVER_DISPUTED, dueA, dueA.minusHours(3));
        transaction(borrower, lender, office, listing, football,
                TransactionStatus.APPROVED, null, null);

        TrustProfileResponse profile =
                trustProfileService.getTrustProfile(borrower.getId(), office.getId());

        assertThat(profile.communityId()).isEqualTo(office.getId());
        assertThat(profile.communityName()).isEqualTo("Engineering Office");
        assertThat(profile.itemsBorrowed()).isEqualTo(3);
        assertThat(profile.itemsLent()).isEqualTo(0);
        assertThat(profile.successfulTransactions()).isEqualTo(2);
        assertThat(profile.completedLoans()).isEqualTo(2);
        assertThat(profile.onTimeReturns()).isEqualTo(1);
        assertThat(profile.lateReturns()).isEqualTo(1);
        assertThat(profile.onTimeReturnRate()).isEqualTo(0.5);
    }

    @Test
    void lenderProfileCountsTheSuccessfulUnionAcrossRoles() {
        seedDataInitializer.seed();
        User borrower = isolatedUser("Borrower");
        User lender = isolatedUser("Lender");
        Community office = seedCommunity("Engineering Office");
        join(lender, office, MembershipStatus.ACTIVE);
        Asset football = assetRepository.findByOwnerId(
                        userRepository.findByEmail("ahmed@example.com").orElseThrow().getId()).stream()
                .filter(a -> a.getTitle().equals("Football"))
                .findFirst()
                .orElseThrow();
        CommunityListing listing = communityListingRepository
                .findByAssetIdAndCommunityId(football.getId(), office.getId())
                .orElseThrow();

        LocalDateTime due = LocalDateTime.of(2026, 6, 10, 9, 0);
        transaction(borrower, lender, office, listing, football,
                TransactionStatus.COMPLETED, due, due.minusHours(1));
        transaction(borrower, lender, office, listing, football,
                TransactionStatus.COMPLETED, due, due.plusHours(5));

        TrustProfileResponse profile =
                trustProfileService.getTrustProfile(lender.getId(), office.getId());

        assertThat(profile.itemsLent()).isEqualTo(2);
        assertThat(profile.itemsBorrowed()).isEqualTo(0);
        assertThat(profile.successfulTransactions()).isEqualTo(2);
        assertThat(profile.completedLoans()).isEqualTo(0);
        assertThat(profile.onTimeReturnRate()).isNull();
    }

    @Test
    void globalScopeCoversEveryCommunity() {
        seedDataInitializer.seed();
        User borrower = isolatedUser("Borrower");
        User lender = isolatedUser("Lender");
        Community office = seedCommunity("Engineering Office");
        join(borrower, office, MembershipStatus.ACTIVE);
        Asset football = assetRepository.findByOwnerId(
                        userRepository.findByEmail("ahmed@example.com").orElseThrow().getId()).stream()
                .filter(a -> a.getTitle().equals("Football"))
                .findFirst()
                .orElseThrow();
        CommunityListing listing = communityListingRepository
                .findByAssetIdAndCommunityId(football.getId(), office.getId())
                .orElseThrow();

        LocalDateTime due = LocalDateTime.of(2026, 6, 10, 9, 0);
        transaction(borrower, lender, office, listing, football,
                TransactionStatus.COMPLETED, due, due.minusHours(1));

        TrustProfileResponse profile = trustProfileService.getTrustProfile(borrower.getId(), null);

        assertThat(profile.communityId()).isNull();
        assertThat(profile.communityName()).isNull();
        assertThat(profile.itemsBorrowed()).isEqualTo(1);
        assertThat(profile.successfulTransactions()).isEqualTo(1);
        assertThat(profile.completedLoans()).isEqualTo(1);
        assertThat(profile.onTimeReturns()).isEqualTo(1);
        assertThat(profile.onTimeReturnRate()).isEqualTo(1.0);
    }

    @Test
    void userWithoutCompletedLoansHasNullRate() {
        seedDataInitializer.seed();
        User fresh = isolatedUser("Fresh");
        Community office = seedCommunity("Engineering Office");
        join(fresh, office, MembershipStatus.ACTIVE);

        TrustProfileResponse profile =
                trustProfileService.getTrustProfile(fresh.getId(), office.getId());

        assertThat(profile.itemsBorrowed()).isEqualTo(0);
        assertThat(profile.itemsLent()).isEqualTo(0);
        assertThat(profile.successfulTransactions()).isEqualTo(0);
        assertThat(profile.completedLoans()).isEqualTo(0);
        assertThat(profile.onTimeReturns()).isEqualTo(0);
        assertThat(profile.lateReturns()).isEqualTo(0);
        assertThat(profile.onTimeReturnRate()).isNull();
    }

    @Test
    void missingMembershipCommunityScopeIsForbidden() {
        seedDataInitializer.seed();
        User user = isolatedUser("Outsider");
        Community office = seedCommunity("Engineering Office");
        Community hostel = seedCommunity("Hostel Block B");
        join(user, office, MembershipStatus.ACTIVE);

        assertThatThrownBy(() -> trustProfileService.getTrustProfile(user.getId(), hostel.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not an active member");
    }

    @Test
    void nonActiveMembershipCommunityScopeIsForbidden() {
        seedDataInitializer.seed();
        User user = isolatedUser("Pending");
        Community office = seedCommunity("Engineering Office");
        Community cse = seedCommunity("CSE Department");
        join(user, office, MembershipStatus.ACTIVE);
        join(user, cse, MembershipStatus.PENDING);

        assertThatThrownBy(() -> trustProfileService.getTrustProfile(user.getId(), cse.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not an active member");
    }
}
