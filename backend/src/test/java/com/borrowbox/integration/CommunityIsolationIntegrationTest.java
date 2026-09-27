package com.borrowbox.integration;

import com.borrowbox.dto.AssetCreateRequest;
import com.borrowbox.dto.AssetResponse;
import com.borrowbox.dto.CommunityRuleRequest;
import com.borrowbox.dto.CommunityRuleResponse;
import com.borrowbox.dto.CommunityRuleUpdateRequest;
import com.borrowbox.dto.EvidenceResponse;
import com.borrowbox.dto.ListingCreateRequest;
import com.borrowbox.dto.ListingResponse;
import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.dto.TransactionCreateRequest;
import com.borrowbox.dto.TransactionDecisionRequest;
import com.borrowbox.dto.TransactionResponse;
import com.borrowbox.dto.WaitlistEntryResponse;
import com.borrowbox.dto.WaitlistJoinRequest;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.CommunityRule;
import com.borrowbox.entity.CommunityRuleType;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.EvidenceType;
import com.borrowbox.entity.Flag;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.WaitlistStatus;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.AssetUnitRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.CommunityRuleRepository;
import com.borrowbox.repository.FlagRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.AssetService;
import com.borrowbox.service.CommunityDashboardService;
import com.borrowbox.service.CommunityListingService;
import com.borrowbox.service.CommunityRuleService;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.FlagService;
import com.borrowbox.service.MembershipService;
import com.borrowbox.service.TransactionMessageService;
import com.borrowbox.service.TransactionService;
import com.borrowbox.service.WaitlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V2.5.2 two-community isolation suite. Builds a fully dynamic world (two
 * communities A and B, two managers, an owner with one global asset listed in
 * both communities, plus members in each) and proves that every cross-scope
 * read and write is rejected while the same actor keeps full access to its own
 * scope. Rows are created by the V242 fixture, so assertions never depend on
 * seeded ids; the class is @Transactional so every scenario rolls back.
 *
 * Scope-per-test, never a generic "cross-community" catch-all: OWNER-scoped
 * (global asset), COMMUNITY-scoped reads, COMMUNITY-scoped writes (rules,
 * flags, member moderation), PARTICIPANT-scoped transactions, MANAGER-scoped
 * operations, and ASSET-scoped waitlist queues. Existing suites already prove
 * flag read/write isolation, moderation scoping and waitlist shared-queue
 * behaviour; this suite only adds the genuinely missing invariants.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CommunityIsolationIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetUnitRepository assetUnitRepository;
    @Autowired private CommunityListingRepository communityListingRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private FlagRepository flagRepository;
    @Autowired private CommunityRuleRepository ruleRepository;

    @Autowired private CommunityService communityService;
    @Autowired private CommunityListingService listingService;
    @Autowired private CommunityRuleService ruleService;
    @Autowired private MembershipService membershipService;
    @Autowired private CommunityDashboardService dashboardService;
    @Autowired private FlagService flagService;
    @Autowired private AssetService assetService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionMessageService messageService;
    @Autowired private WaitlistService waitlistService;

    private record World(
            Community communityA,
            Community communityB,
            User managerA,
            User managerB,
            User coManagerA,
            User memberA,
            User memberB,
            User ownerShare,
            Asset sharedAsset,
            CommunityListing listingA,
            CommunityListing listingB
    ) {
    }

    private World world() {
        User managerA = V242IntegrationFixture.newUser(userRepository, "IsoMgrA");
        User managerB = V242IntegrationFixture.newUser(userRepository, "IsoMgrB");
        User coManagerA = V242IntegrationFixture.newUser(userRepository, "IsoCoMgrA");
        User memberA = V242IntegrationFixture.newUser(userRepository, "IsoMemberA");
        User memberB = V242IntegrationFixture.newUser(userRepository, "IsoMemberB");
        User ownerShare = V242IntegrationFixture.newUser(userRepository, "IsoOwner");

        Community communityA = V242IntegrationFixture.newCommunity(communityService, communityRepository, managerA);
        Community communityB = V242IntegrationFixture.newCommunity(communityService, communityRepository, managerB);

        V242IntegrationFixture.joinActive(membershipRepository, coManagerA, communityA, MembershipRole.MANAGER);
        V242IntegrationFixture.joinActive(membershipRepository, memberA, communityA, MembershipRole.MEMBER);
        V242IntegrationFixture.joinActive(membershipRepository, memberB, communityB, MembershipRole.MEMBER);
        // The owner of the shared asset is an active member of BOTH communities
        // so the very same global asset can be listed in each.
        V242IntegrationFixture.joinActive(membershipRepository, ownerShare, communityA, MembershipRole.MEMBER);
        V242IntegrationFixture.joinActive(membershipRepository, ownerShare, communityB, MembershipRole.MEMBER);

        Asset sharedAsset = V242IntegrationFixture.newAsset(
                assetRepository, ownerShare, "Isolation Shared " + UUID.randomUUID());
        CommunityListing listingA = V242IntegrationFixture.newListing(communityListingRepository, sharedAsset, communityA);
        CommunityListing listingB = V242IntegrationFixture.newListing(communityListingRepository, sharedAsset, communityB);

        return new World(communityA, communityB, managerA, managerB, coManagerA,
                memberA, memberB, ownerShare, sharedAsset, listingA, listingB);
    }

    private Membership membershipOf(User user, Community community) {
        return membershipRepository.findByUserIdAndCommunityId(user.getId(), community.getId()).orElseThrow();
    }

    private MockMultipartFile photo(String name, byte[] content) {
        return new MockMultipartFile("file", name, "image/png", content);
    }

    private long countUnitStatus(Long assetId, AssetUnitStatus status) {
        return assetUnitRepository.findByAssetId(assetId).stream()
                .filter(u -> u.getStatus() == status)
                .count();
    }

    // ── 2. Community-scoped reads ──────────────────────────────────────

    @Test
    void memberOfAReadsListingsOfAButNeverOfB() {
        World w = world();

        assertThat(listingService.listForCommunity(w.communityA().getId(), w.memberA()))
                .extracting(ListingResponse::assetId)
                .contains(w.sharedAsset().getId());
        assertThatThrownBy(() -> listingService.listForCommunity(w.communityB().getId(), w.memberA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(listingService.listForCommunity(w.communityB().getId(), w.memberB()))
                .extracting(ListingResponse::assetId)
                .contains(w.sharedAsset().getId());
    }

    @Test
    void memberOfAEnumeratesMembersOfAButNeverOfB() {
        World w = world();

        List<MembershipResponse> ownDirectory =
                membershipService.listMembers(w.memberA().getId(), w.communityA().getId(), null, null);
        assertThat(ownDirectory)
                .extracting(MembershipResponse::userId)
                .contains(w.memberA().getId());

        assertThatThrownBy(() ->
                membershipService.listMembers(w.memberA().getId(), w.communityB().getId(), null, null))
                .isInstanceOf(UnauthorizedException.class);

        List<MembershipResponse> bDirectory =
                membershipService.listMembers(w.memberB().getId(), w.communityB().getId(), null, null);
        assertThat(bDirectory)
                .extracting(MembershipResponse::userId)
                .contains(w.memberB().getId());
    }

    @Test
    void memberOfAReadsActiveRulesOfAButNotOfB() {
        World w = world();

        ruleService.createRule(w.communityA().getId(),
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("note", "Be kind")),
                w.managerA());

        assertThat(ruleService.listActiveRules(w.communityA().getId(), w.memberA()))
                .extracting(CommunityRuleResponse::ruleType)
                .contains(CommunityRuleType.ADMISSION_NOTE);
        assertThatThrownBy(() -> ruleService.listActiveRules(w.communityB().getId(), w.memberA()))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── 3. Community-scoped writes (rules manager scope) ───────────────

    @Test
    void managerOfACreatesRulesInAButCannotCreateUpdateOrDeactivateRulesOfB() {
        World w = world();

        ruleService.createRule(w.communityA().getId(),
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("note", "A note")),
                w.managerA());
        assertThat(ruleService.listRulesForCommunity(w.communityA().getId(), w.managerA()))
                .extracting(CommunityRuleResponse::ruleType)
                .contains(CommunityRuleType.ADMISSION_NOTE);

        CommunityRuleResponse inB = ruleService.createRule(w.communityB().getId(),
                new CommunityRuleRequest(CommunityRuleType.OVERDUE_GRACE_PERIOD, Map.of("days", 3)),
                w.managerB());

        assertThatThrownBy(() -> ruleService.createRule(w.communityB().getId(),
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("note", "cross")),
                w.managerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> ruleService.updateRule(w.communityB().getId(), inB.id(),
                new CommunityRuleUpdateRequest(null, false), w.managerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> ruleService.deactivateRule(w.communityB().getId(), inB.id(), w.managerA()))
                .isInstanceOf(UnauthorizedException.class);

        CommunityRule reloaded = ruleRepository.findById(inB.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(reloaded.getUpdatedBy().getId()).isEqualTo(w.managerB().getId());
    }

    // ── 3. Community-scoped writes (member moderation) ─────────────────

    @Test
    void managerOfACanModerateMembersOfAButCannotTouchMembersOfB() {
        World w = world();

        Membership memberARow = membershipOf(w.memberA(), w.communityA());
        membershipService.suspend(memberARow.getId(), w.managerA());
        assertThat(membershipRepository.findById(memberARow.getId()).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.SUSPENDED);
        membershipService.reinstate(memberARow.getId(), w.managerA());
        assertThat(membershipRepository.findById(memberARow.getId()).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.ACTIVE);

        Membership memberBRow = membershipOf(w.memberB(), w.communityB());
        assertThatThrownBy(() -> membershipService.suspend(memberBRow.getId(), w.managerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> membershipService.removeMember(memberBRow.getId(), w.managerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(membershipRepository.findById(memberBRow.getId()).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.ACTIVE);
    }

    // ── 3. Community-scoped writes (flags) ─────────────────────────────

    @Test
    void managerOfASeesAndMutatesFlagsOfAButCannotReachFlagsOfB() {
        World w = world();

        Flag inA = flagService.createFlag(w.communityA().getId(), null,
                FlagType.MANUAL, "A manual flag", w.managerA());
        assertThat(flagService.listFiltered(w.communityA().getId(), null, null, null, w.managerA()))
                .extracting(Flag::getId)
                .containsExactly(inA.getId());

        Flag inB = flagService.createFlag(w.communityB().getId(), null,
                FlagType.OVERDUE, "B overdue flag", w.managerB());
        assertThatThrownBy(() -> flagService.getFlag(w.communityB().getId(), inB.getId(), w.managerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> flagService.listFiltered(w.communityB().getId(), null, null, null, w.managerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> flagService.updateFlag(w.communityB().getId(), inB.getId(),
                new com.borrowbox.dto.FlagUpdateRequest(FlagStatus.REVIEWED, null, false, null), w.managerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(flagRepository.findById(inB.getId()).orElseThrow().getStatus())
                .isEqualTo(FlagStatus.OPEN);
    }

    // ── 4. Owner-scoped global asset ───────────────────────────────────

    @Test
    void ownerListsTheSameAssetInBothAAndB() {
        World w = world();

        assertThat(communityListingRepository.findByAssetIdAndCommunityId(
                w.sharedAsset().getId(), w.communityA().getId())).isPresent();
        assertThat(communityListingRepository.findByAssetIdAndCommunityId(
                w.sharedAsset().getId(), w.communityB().getId())).isPresent();
        assertThat(listingService.listForAsset(w.sharedAsset().getId(), w.ownerShare()))
                .extracting(ListingResponse::id)
                .containsExactlyInAnyOrder(w.listingA().getId(), w.listingB().getId());
    }

    @Test
    void nonOwnerCannotCreateReactivateOrUnlistAnotherOwnersListing() {
        World w = world();

        Asset owned = V242IntegrationFixture.newAsset(
                assetRepository, w.ownerShare(), "Isolation Owned " + UUID.randomUUID());
        assertThat(listingService.createOrReactivate(owned.getId(),
                new ListingCreateRequest(w.communityA().getId()), w.ownerShare()).created()).isTrue();

        assertThatThrownBy(() -> listingService.createOrReactivate(owned.getId(),
                new ListingCreateRequest(w.communityB().getId()), w.memberA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> listingService.unlist(owned.getId(), w.communityA().getId(), w.memberA()))
                .isInstanceOf(UnauthorizedException.class);

        listingService.unlist(owned.getId(), w.communityA().getId(), w.ownerShare());
        assertThat(listingService.createOrReactivate(owned.getId(),
                new ListingCreateRequest(w.communityA().getId()), w.ownerShare()).created()).isFalse();
    }

    @Test
    void nonOwnerCannotReadOrArchiveAnotherOwnersAsset() {
        World w = world();

        Asset owned = V242IntegrationFixture.newAsset(
                assetRepository, w.ownerShare(), "Isolation Owned " + UUID.randomUUID());

        assertThatThrownBy(() -> assetService.getAssetById(owned.getId(), w.memberA()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> assetService.archiveAsset(owned.getId(), w.memberA()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(assetRepository.findById(owned.getId()).orElseThrow().getStatus())
                .isEqualTo(AssetStatus.ACTIVE);
    }

    // ── 5. Participant-scoped transactions ─────────────────────────────

    private Transaction approvedTransactionInA(World w) {
        User lenderA = V242IntegrationFixture.newUser(userRepository, "IsoLenderA");
        V242IntegrationFixture.joinActive(membershipRepository, lenderA, w.communityA(), MembershipRole.MEMBER);
        return V242IntegrationFixture.newTransaction(transactionRepository, w.communityA(),
                w.listingA(), w.sharedAsset(), w.memberA(), lenderA,
                TransactionStatus.APPROVED, null, null);
    }

    @Test
    void outsiderFromAnotherCommunityCannotViewTransaction() {
        World w = world();
        Transaction txn = approvedTransactionInA(w);

        assertThat(transactionService.view(txn.getId(), w.memberA())).isNotNull();
        assertThat(transactionService.view(txn.getId(), txn.getLender())).isNotNull();
        assertThatThrownBy(() -> transactionService.view(txn.getId(), w.memberB()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void outsiderFromAnotherCommunityCannotReadOrSendMessages() {
        World w = world();
        Transaction txn = approvedTransactionInA(w);

        assertThatThrownBy(() -> messageService.listMessages(txn.getId(), w.memberB()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> messageService.sendMessage(txn.getId(), w.memberB(), "let me in"))
                .isInstanceOf(UnauthorizedException.class);

        messageService.sendMessage(txn.getId(), w.memberA(), "Friday works");
        assertThat(messageService.listMessages(txn.getId(), txn.getLender()))
                .hasSize(1);
    }

    @Test
    void outsiderFromAnotherCommunityCannotListOrReadEvidence() {
        World w = world();
        User lenderA = V242IntegrationFixture.newUser(userRepository, "IsoLenderA2");
        V242IntegrationFixture.joinActive(membershipRepository, lenderA, w.communityA(), MembershipRole.MEMBER);
        Transaction txn = V242IntegrationFixture.newTransaction(transactionRepository, w.communityA(),
                w.listingA(), w.sharedAsset(), w.memberA(), lenderA,
                TransactionStatus.AWAITING_HANDOVER, null, null);

        EvidenceResponse evidence = transactionService.uploadEvidence(txn.getId(),
                EvidenceType.LENDER_HANDOVER, photo("handover.png", new byte[]{2}), null, null, lenderA);

        assertThat(transactionService.listEvidence(txn.getId(), w.memberA())).hasSize(1);
        assertThat(transactionService.getEvidenceContent(evidence.id(), w.memberA())).isNotNull();
        assertThatThrownBy(() -> transactionService.listEvidence(txn.getId(), w.memberB()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> transactionService.getEvidenceContent(evidence.id(), w.memberB()))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── 6. Asset-scoped waitlist queues ────────────────────────────────

    @Test
    void differentAssetsRetainIndependentWaitlistQueues() {
        World w = world();
        Long aId = w.communityA().getId();
        User owner = V242IntegrationFixture.newUser(userRepository, "IsoWlOwner");
        V242IntegrationFixture.joinActive(membershipRepository, owner, w.communityA(), MembershipRole.MEMBER);

        AssetResponse assetX = assetService.createAsset(
                new AssetCreateRequest("Iso Queue X " + UUID.randomUUID(), "desc", null, 1), owner);
        AssetResponse assetY = assetService.createAsset(
                new AssetCreateRequest("Iso Queue Y " + UUID.randomUUID(), "desc", null, 1), owner);
        Long listingX = listingService.createOrReactivate(assetX.id(),
                new ListingCreateRequest(aId), owner).response().id();
        Long listingY = listingService.createOrReactivate(assetY.id(),
                new ListingCreateRequest(aId), owner).response().id();

        User holderX = V242IntegrationFixture.newUser(userRepository, "IsoWlHolderX");
        User holderY = V242IntegrationFixture.newUser(userRepository, "IsoWlHolderY");
        V242IntegrationFixture.joinActive(membershipRepository, holderX, w.communityA(), MembershipRole.MEMBER);
        V242IntegrationFixture.joinActive(membershipRepository, holderY, w.communityA(), MembershipRole.MEMBER);
        TransactionResponse holdX = transactionService.create(
                new TransactionCreateRequest(listingX, "holding X", 1, null), holderX);
        TransactionResponse holdY = transactionService.create(
                new TransactionCreateRequest(listingY, "holding Y", 1, null), holderY);

        User waiterX = V242IntegrationFixture.newUser(userRepository, "IsoWlWaiterX");
        User waiterY = V242IntegrationFixture.newUser(userRepository, "IsoWlWaiterY");
        V242IntegrationFixture.joinActive(membershipRepository, waiterX, w.communityA(), MembershipRole.MEMBER);
        V242IntegrationFixture.joinActive(membershipRepository, waiterY, w.communityA(), MembershipRole.MEMBER);
        WaitlistEntryResponse posX = waitlistService.join(listingX,
                new WaitlistJoinRequest("want X", 2), waiterX);
        WaitlistEntryResponse posY = waitlistService.join(listingY,
                new WaitlistJoinRequest("want Y", 2), waiterY);

        assertThat(posX.position()).isEqualTo(1);
        assertThat(posY.position()).isEqualTo(1);

        transactionService.reject(holdX.id(), new TransactionDecisionRequest("release X"), owner);

        assertThat(waitlistService.listForBorrower(waiterX)).isEmpty();
        assertThat(transactionRepository.findByBorrowerIdOrderByIdDesc(waiterX.getId()))
                .extracting(Transaction::getState)
                .containsExactly(TransactionStatus.PENDING);
        assertThat(waitlistService.listForBorrower(waiterY)).hasSize(1);
        assertThat(waitlistService.listForBorrower(waiterY).get(0).status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(waitlistService.listForBorrower(waiterY).get(0).position()).isEqualTo(1);
        assertThat(transactionRepository.findByBorrowerIdOrderByIdDesc(waiterY.getId())).isEmpty();
        assertThat(countUnitStatus(assetY.id(), AssetUnitStatus.RESERVED)).isEqualTo(1);
    }

    // ── 7/8. Manager scope and active role ─────────────────────────────

    @Test
    void ordinaryMemberCannotExecuteManagerOperationsInOwnCommunity() {
        World w = world();
        Long aId = w.communityA().getId();

        CommunityRuleResponse ruleA = ruleService.createRule(aId,
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("note", "gate")),
                w.managerA());

        assertThatThrownBy(() -> ruleService.createRule(aId,
                new CommunityRuleRequest(CommunityRuleType.MAX_ACTIVE_MEMBERS, Map.of("max", 10)),
                w.memberA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> ruleService.listRulesForCommunity(aId, w.memberA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> ruleService.deactivateRule(aId, ruleA.id(), w.memberA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> flagService.createFlag(aId, null, FlagType.MANUAL, "member-flag", w.memberA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> dashboardService.getDashboard(aId, w.memberA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> membershipService.suspend(membershipOf(w.coManagerA(), w.communityA()).getId(), w.memberA()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void suspendedManagerCannotExecuteManagerOperations() {
        World w = world();
        Long aId = w.communityA().getId();

        membershipService.suspend(membershipOf(w.coManagerA(), w.communityA()).getId(), w.managerA());
        assertThat(membershipRepository.findByUserIdAndCommunityId(
                w.coManagerA().getId(), aId).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.SUSPENDED);

        assertThatThrownBy(() -> ruleService.createRule(aId,
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("note", "suspended")),
                w.coManagerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> flagService.createFlag(aId, null, FlagType.MANUAL, "suspended", w.coManagerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> dashboardService.getDashboard(aId, w.coManagerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> membershipService.suspend(membershipOf(w.memberA(), w.communityA()).getId(), w.coManagerA()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void managerOfACannotReadDashboardOrHealthOfB() {
        World w = world();

        assertThat(dashboardService.getDashboard(w.communityA().getId(), w.managerA())).isNotNull();
        assertThat(dashboardService.getHealth(w.communityA().getId(), w.managerA())).isNotNull();
        assertThat(dashboardService.getDashboard(w.communityB().getId(), w.managerB())).isNotNull();

        assertThatThrownBy(() -> dashboardService.getDashboard(w.communityB().getId(), w.managerA()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> dashboardService.getHealth(w.communityB().getId(), w.managerA()))
                .isInstanceOf(UnauthorizedException.class);
    }
}
