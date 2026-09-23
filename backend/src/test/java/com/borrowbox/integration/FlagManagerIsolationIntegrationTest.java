package com.borrowbox.integration;

import com.borrowbox.dto.FlagUpdateRequest;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.Flag;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.FlagRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.FlagService;
import jakarta.persistence.EntityManager;
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
 * V2.4.2 manager-only flag management against the real database: the id-based
 * create path (reporter is always the acting manager), the patch-style update
 * lease (status / assignee / note updated independently), combined filters,
 * and strict community isolation for every query and mutation.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class FlagManagerIsolationIntegrationTest {

    @Autowired
    private FlagService flagService;
    @Autowired
    private CommunityService communityService;
    @Autowired
    private FlagRepository flagRepository;
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
    private EntityManager entityManager;

    private record World(Community community, User manager, User coManager, User member,
                         CommunityListing listing, Asset asset) {
    }

    private World world() {
        User manager = V242IntegrationFixture.newUser(userRepository, "FlagMgrA");
        User coManager = V242IntegrationFixture.newUser(userRepository, "FlagCoMgrA");
        User member = V242IntegrationFixture.newUser(userRepository, "FlagMemberA");
        Community community = V242IntegrationFixture.newCommunity(communityService, communityRepository, manager);
        V242IntegrationFixture.joinActive(membershipRepository, coManager, community, MembershipRole.MANAGER);
        V242IntegrationFixture.joinActive(membershipRepository, member, community, MembershipRole.MEMBER);
        Asset asset = V242IntegrationFixture.newAsset(assetRepository, member, "Flag Ladder");
        CommunityListing listing = V242IntegrationFixture.newListing(communityListingRepository, asset, community);
        return new World(community, manager, coManager, member, listing, asset);
    }

    private Transaction transaction(World w, Community community) {
        User lender = V242IntegrationFixture.newUser(userRepository, "FlagBorrowerLender");
        return V242IntegrationFixture.newTransaction(transactionRepository, community, w.listing(), w.asset(),
                w.member(), lender, TransactionStatus.ACTIVE, LocalDateTime.now().minusDays(2), null);
    }

    private Flag reload(Long id) {
        entityManager.flush();
        entityManager.clear();
        return flagRepository.findById(id).orElseThrow();
    }

    @Test
    void createByTransactionIdStampsReporterAsActingManager() {
        World w = world();
        Transaction txn = transaction(w, w.community());

        Flag flag = flagService.createFlag(w.community().getId(), txn.getId(),
                FlagType.OVERDUE, "still out overdue", w.manager());

        Flag loaded = reload(flag.getId());
        assertThat(loaded.getCommunity().getId()).isEqualTo(w.community().getId());
        assertThat(loaded.getTransaction().getId()).isEqualTo(txn.getId());
        assertThat(loaded.getFlagType()).isEqualTo(FlagType.OVERDUE);
        assertThat(loaded.getStatus()).isEqualTo(FlagStatus.OPEN);
        assertThat(loaded.getReporter().getId()).isEqualTo(w.manager().getId());
        assertThat(loaded.getNote()).isEqualTo("still out overdue");
        assertThat(loaded.getOccurredAt()).isNotNull();
        assertThat(loaded.getAssignee()).isNull();
    }

    @Test
    void createWithNullTransactionIdOpensManualFlag() {
        World w = world();

        Flag flag = flagService.createFlag(w.community().getId(), null,
                FlagType.MANUAL, "broken shelf", w.manager());

        Flag loaded = reload(flag.getId());
        assertThat(loaded.getTransaction()).isNull();
        assertThat(loaded.getFlagType()).isEqualTo(FlagType.MANUAL);
    }

    @Test
    void createRejectsMissingAndCrossCommunityTransactions() {
        World w = world();
        User managerB = V242IntegrationFixture.newUser(userRepository, "FlagMgrB");
        Community communityB = V242IntegrationFixture.newCommunity(communityService, communityRepository, managerB);
        Transaction foreignTxn = transaction(w, communityB);

        assertThatThrownBy(() -> flagService.createFlag(w.community().getId(), 999_999_999L,
                FlagType.MANUAL, "nope", w.manager()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> flagService.createFlag(w.community().getId(), foreignTxn.getId(),
                FlagType.MANUAL, "nope", w.manager()))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> flagService.createFlag(w.community().getId(), null,
                null, "missing type", w.manager()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void nonManagerCannotCreateOrReadFlags() {
        World w = world();
        Transaction txn = transaction(w, w.community());

        assertThatThrownBy(() -> flagService.createFlag(w.community().getId(), txn.getId(),
                FlagType.OVERDUE, "no", w.member()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> flagService.listFiltered(w.community().getId(), null, null, null, w.member()))
                .isInstanceOf(UnauthorizedException.class);

        Flag created = flagService.createFlag(w.community().getId(), null,
                FlagType.MANUAL, "private", w.manager());
        User outsider = V242IntegrationFixture.newUser(userRepository, "FlagOutsider");
        assertThatThrownBy(() -> flagService.getFlag(w.community().getId(), created.getId(), outsider))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void updatePatchesStatusAssigneeAndNoteIndependently() {
        World w = world();
        Flag flag = flagService.createFlag(w.community().getId(), null,
                FlagType.RETURN_DISPUTED, "not returned", w.manager());

        flagService.updateFlag(w.community().getId(), flag.getId(),
                new FlagUpdateRequest(FlagStatus.REVIEWED, null, false, null), w.manager());
        assertThat(reload(flag.getId()).getStatus()).isEqualTo(FlagStatus.REVIEWED);

        flagService.updateFlag(w.community().getId(), flag.getId(),
                new FlagUpdateRequest(null, w.coManager().getId(), false, null), w.manager());
        Flag assigned = reload(flag.getId());
        assertThat(assigned.getAssignee().getId()).isEqualTo(w.coManager().getId());

        flagService.updateFlag(w.community().getId(), flag.getId(),
                new FlagUpdateRequest(null, null, true, "chasing the lender"), w.manager());
        Flag updated = reload(flag.getId());
        assertThat(updated.getAssignee()).isNull();
        assertThat(updated.getNote()).isEqualTo("chasing the lender");
        assertThat(updated.getStatus()).isEqualTo(FlagStatus.REVIEWED);
        assertThat(updated.getReporter().getId()).isEqualTo(w.manager().getId());
        assertThat(updated.getFlagType()).isEqualTo(FlagType.RETURN_DISPUTED);
    }

    @Test
    void updateRejectsAssignUnassignConflictAndNonManagerAssignee() {
        World w = world();
        Flag flag = flagService.createFlag(w.community().getId(), null,
                FlagType.MANUAL, "triage me", w.manager());

        assertThatThrownBy(() -> flagService.updateFlag(w.community().getId(), flag.getId(),
                new FlagUpdateRequest(null, w.coManager().getId(), true, null), w.manager()))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> flagService.updateFlag(w.community().getId(), flag.getId(),
                new FlagUpdateRequest(null, w.member().getId(), false, null), w.manager()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> flagService.updateFlag(w.community().getId(), flag.getId(),
                new FlagUpdateRequest(null, null, false, null), w.member()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void listFilteredSupportsCombinedFiltersAndNewestFirst() {
        World w = world();
        Transaction txn = transaction(w, w.community());
        User manager = w.manager();
        Long communityId = w.community().getId();

        Flag f1 = flagService.createFlag(communityId, txn.getId(), FlagType.OVERDUE, "overdue one", manager);
        Flag f2 = flagService.createFlag(communityId, null, FlagType.MANUAL, "manual two", manager);
        flagService.updateFlag(communityId, f2.getId(), new FlagUpdateRequest(FlagStatus.DISMISSED, null, false, null), manager);
        Flag f3 = flagService.createFlag(communityId, null, FlagType.OVERDUE, "overdue three", manager);

        assertThat(flagService.listFiltered(communityId, FlagStatus.OPEN, FlagType.OVERDUE, txn.getId(), manager))
                .extracting(Flag::getId)
                .containsExactly(f1.getId());
        assertThat(flagService.listFiltered(communityId, FlagStatus.OPEN, null, null, manager))
                .extracting(Flag::getId)
                .containsExactly(f3.getId(), f1.getId());
        assertThat(flagService.listFiltered(communityId, null, null, null, manager))
                .extracting(Flag::getId)
                .containsExactly(f3.getId(), f2.getId(), f1.getId());
    }

    @Test
    void recentFlagsAndOpenCountReflectWorkflow() {
        World w = world();
        Long communityId = w.community().getId();
        User manager = w.manager();

        Flag f1 = flagService.createFlag(communityId, null, FlagType.MANUAL, "first", manager);
        Flag f2 = flagService.createFlag(communityId, null, FlagType.OVERDUE, "second", manager);
        flagService.updateFlag(communityId, f2.getId(),
                new FlagUpdateRequest(FlagStatus.RESOLVED, null, false, null), manager);

        assertThat(flagService.countOpen(communityId)).isEqualTo(1);
        assertThat(flagService.recentFlags(communityId, manager))
                .extracting(Flag::getId)
                .containsExactly(f2.getId(), f1.getId());
    }

    @Test
    void getFlagFromAnotherCommunityThrowsResourceNotFound() {
        World w = world();
        User managerB = V242IntegrationFixture.newUser(userRepository, "FlagMgrB2");
        Community communityB = V242IntegrationFixture.newCommunity(communityService, communityRepository, managerB);

        Flag created = flagService.createFlag(w.community().getId(), null,
                FlagType.MANUAL, "private to A", w.manager());

        assertThatThrownBy(() -> flagService.getFlag(communityB.getId(), created.getId(), managerB))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> flagService.updateFlag(communityB.getId(), created.getId(),
                new FlagUpdateRequest(FlagStatus.OPEN, null, false, null), managerB))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(flagService.listFiltered(w.community().getId(), null, null, null, w.manager()))
                .extracting(Flag::getId)
                .containsExactly(created.getId());
    }

    @Test
    void listFilteredFlagsStayWithinTheirOwnCommunity() {
        World w = world();
        User managerB = V242IntegrationFixture.newUser(userRepository, "FlagMgrB3");
        Community communityB = V242IntegrationFixture.newCommunity(communityService, communityRepository, managerB);

        Flag inA = flagService.createFlag(w.community().getId(), null, FlagType.MANUAL, "in A", w.manager());
        Flag inB = flagService.createFlag(communityB.getId(), null, FlagType.OVERDUE, "in B", managerB);

        List<Flag> fromA = flagService.listFiltered(w.community().getId(), null, null, null, w.manager());
        List<Flag> fromB = flagService.listFiltered(communityB.getId(), null, null, null, managerB);
        assertThat(fromA).extracting(Flag::getId).containsExactly(inA.getId());
        assertThat(fromB).extracting(Flag::getId).containsExactly(inB.getId());
    }
}