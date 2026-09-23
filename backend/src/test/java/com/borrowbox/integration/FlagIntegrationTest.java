package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.Flag;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.MembershipVerificationMethod;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class FlagIntegrationTest {

    @Autowired
    private FlagService flagService;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private FlagRepository flagRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SeedDataInitializer seedDataInitializer;

    @Autowired
    private EntityManager entityManager;

    private User user(String prefix) {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        return userRepository.findByEmail(email).orElseGet(() -> {
            User nu = new User(prefix, email);
            nu.setPasswordHash("test-password");
            nu.setStatus(UserStatus.ACTIVE);
            return userRepository.save(nu);
        });
    }

    private Community createCommunity(User manager) {
        CommunityCreateRequest req = new CommunityCreateRequest(
                "Flags " + UUID.randomUUID(), null, CommunityType.CLUB, null, null, null, null);
        return communityRepository.findById(communityService.createCommunity(req, manager).id()).orElseThrow();
    }

    private void addActiveManager(Community community, User manager) {
        if (membershipRepository.existsByUserIdAndCommunityId(manager.getId(), community.getId())) {
            return;
        }
        Membership m = new Membership();
        m.setUser(manager);
        m.setCommunity(community);
        m.setRole(MembershipRole.MANAGER);
        m.setStatus(MembershipStatus.ACTIVE);
        m.setVerificationMethod(MembershipVerificationMethod.ADMIN);
        m.setVerifiedBy(manager);
        m.setVerifiedAt(LocalDateTime.now());
        m.setJoinedAt(LocalDateTime.now());
        membershipRepository.save(m);
    }

    private Flag reload(Long id) {
        entityManager.flush();
        entityManager.clear();
        return flagRepository.findById(id).orElseThrow();
    }

    private User seedUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("missing seed user " + email));
    }

    private Community cse() {
        return communityRepository.findAll().stream()
                .filter(c -> c.getName().equals("CSE Department"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed community CSE Department"));
    }

    private Transaction seedCseTransaction() {
        seedDataInitializer.seed();
        return transactionRepository.findAll().stream()
                .filter(t -> t.getCommunity() != null
                        && "CSE Department".equals(t.getCommunity().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seeded CSE transaction"));
    }

    @Test
    void createFlagPersistsCoreFactsAndDefaultsToOpen() {
        User manager = user("FlagMgr");
        Community community = createCommunity(manager);
        User reporter = user("FlagReporter");

        Flag created = flagService.createFlag(community.getId(), null, FlagType.MANUAL, reporter,
                "Reported a broken shelf", manager);
        assertThat(created.getId()).isNotNull();

        Flag loaded = reload(created.getId());
        assertThat(loaded.getCommunity().getId()).isEqualTo(community.getId());
        assertThat(loaded.getFlagType()).isEqualTo(FlagType.MANUAL);
        assertThat(loaded.getStatus()).isEqualTo(FlagStatus.OPEN);
        assertThat(loaded.getReporter().getId()).isEqualTo(reporter.getId());
        assertThat(loaded.getTransaction()).isNull();
        assertThat(loaded.getNote()).isEqualTo("Reported a broken shelf");
        assertThat(loaded.getOccurredAt()).isNotNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isAfterOrEqualTo(loaded.getCreatedAt());
    }

    @Test
    void systemStyleFlagWithNullReporterPersists() {
        User manager = user("FlagMgr2");
        Community community = createCommunity(manager);

        Flag created = flagService.createFlag(community.getId(), null, FlagType.OVERDUE, null, null, manager);

        Flag loaded = reload(created.getId());
        assertThat(loaded.getReporter()).isNull();
        assertThat(loaded.getFlagType()).isEqualTo(FlagType.OVERDUE);
        assertThat(loaded.getNote()).isNull();
    }

    @Test
    void assignAndUnassignAssigneePersists() {
        User manager = user("FlagMgr3");
        Community community = createCommunity(manager);
        User assignee = user("FlagAssignee");
        addActiveManager(community, assignee);

        Flag created = flagService.createFlag(community.getId(), null, FlagType.MANUAL, manager, "triage", manager);
        flagService.assignAssignee(community.getId(), created.getId(), assignee, manager);

        Flag assigned = reload(created.getId());
        assertThat(assigned.getAssignee().getId()).isEqualTo(assignee.getId());

        flagService.assignAssignee(community.getId(), created.getId(), null, manager);
        Flag unassigned = reload(created.getId());
        assertThat(unassigned.getAssignee()).isNull();
    }

    @Test
    void statusWorkflowPersistsAndBumpsUpdatedAt() {
        User manager = user("FlagMgr4");
        Community community = createCommunity(manager);

        Flag created = flagService.createFlag(community.getId(), null, FlagType.EVIDENCE_ISSUE, manager,
                "missing photos", manager);
        Flag before = reload(created.getId());
        LocalDateTime createdAt = before.getCreatedAt();

        flagService.updateStatus(community.getId(), created.getId(), FlagStatus.REVIEWED, manager);
        flagService.updateStatus(community.getId(), created.getId(), FlagStatus.RESOLVED, manager);

        Flag after = reload(created.getId());
        assertThat(after.getStatus()).isEqualTo(FlagStatus.RESOLVED);
        assertThat(after.getCreatedAt()).isEqualTo(createdAt);
        assertThat(after.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void flagLinkedToSeedTransactionPersistsAndQueriesByTransaction() {
        User ahmed = seedUser("ahmed@example.com");
        Community community = cse();
        Transaction backing = seedCseTransaction();

        Flag created = flagService.createFlag(community.getId(), backing, FlagType.HANDOVER_DISPUTED,
                ahmed, "disputed handover", ahmed);

        Flag loaded = reload(created.getId());
        assertThat(loaded.getTransaction().getId()).isEqualTo(backing.getId());

        List<Flag> byTransaction = flagService.listByTransaction(community.getId(), backing.getId(), ahmed);
        assertThat(byTransaction).hasSize(1);
        assertThat(byTransaction.get(0).getId()).isEqualTo(created.getId());
    }

    @Test
    void coreIncidentFactsStayImmutableAcrossWorkflowMutations() {
        User manager = user("FlagMgr5");
        Community community = createCommunity(manager);
        User reporter = user("FlagReporter2");

        Flag created = flagService.createFlag(community.getId(), null, FlagType.RETURN_DISPUTED, reporter,
                "not received", manager);
        Flag snapshot = reload(created.getId());

        flagService.updateStatus(community.getId(), created.getId(), FlagStatus.REVIEWED, manager);
        flagService.updateNote(community.getId(), created.getId(), "lender claiming", manager);
        flagService.assignAssignee(community.getId(), created.getId(), null, manager);

        Flag after = reload(created.getId());
        assertThat(after.getStatus()).isEqualTo(FlagStatus.REVIEWED);
        assertThat(after.getCommunity().getId()).isEqualTo(community.getId());
        assertThat(after.getFlagType()).isEqualTo(FlagType.RETURN_DISPUTED);
        assertThat(after.getReporter().getId()).isEqualTo(reporter.getId());
        assertThat(after.getNote()).isEqualTo("lender claiming");
        assertThat(after.getOccurredAt()).isEqualTo(snapshot.getOccurredAt());
        assertThat(after.getCreatedAt()).isEqualTo(snapshot.getCreatedAt());
    }

    @Test
    void queryByStatusReturnsOnlyMatching() {
        User manager = user("FlagMgr6");
        Community community = createCommunity(manager);
        Flag open = flagService.createFlag(community.getId(), null, FlagType.MANUAL, manager, "one", manager);
        Flag closed = flagService.createFlag(community.getId(), null, FlagType.MANUAL, manager, "two", manager);
        flagService.updateStatus(community.getId(), closed.getId(), FlagStatus.DISMISSED, manager);

        List<Flag> openOnly = reloaded(flagRepository.findByCommunityIdAndStatus(community.getId(), FlagStatus.OPEN));
        List<Flag> all = reloaded(flagRepository.findByCommunityIdAndStatus(community.getId(), FlagStatus.DISMISSED));
        assertThat(openOnly).hasSize(1);
        assertThat(openOnly.get(0).getId()).isEqualTo(open.getId());
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getId()).isEqualTo(closed.getId());
    }

    @Test
    void queryByFlagTypeReturnsOnlyMatching() {
        User manager = user("FlagMgr7");
        Community community = createCommunity(manager);
        Flag overdue = flagService.createFlag(community.getId(), null, FlagType.OVERDUE, manager, "late", manager);
        flagService.createFlag(community.getId(), null, FlagType.MANUAL, manager, "shelf", manager);

        List<Flag> overdueOnly = reloaded(
                flagRepository.findByCommunityIdAndFlagType(community.getId(), FlagType.OVERDUE));

        assertThat(overdueOnly).hasSize(1);
        assertThat(overdueOnly.get(0).getId()).isEqualTo(overdue.getId());
    }

    @Test
    void listByCommunityReturnsAllFlagsRegardlessOfStatus() {
        User manager = user("FlagMgr8");
        Community community = createCommunity(manager);
        Flag first = flagService.createFlag(community.getId(), null, FlagType.MANUAL, manager, "one", manager);
        Flag second = flagService.createFlag(community.getId(), null, FlagType.OVERDUE, manager, "two", manager);
        flagService.updateStatus(community.getId(), second.getId(), FlagStatus.RESOLVED, manager);

        List<Flag> all = flagService.listByCommunity(community.getId(), manager);

        assertThat(all).hasSize(2);
        assertThat(all).extracting(Flag::getId).contains(first.getId(), second.getId());
    }

    @Test
    void nonManagerCannotCreateFlag() {
        User manager = user("FlagMgr9");
        Community community = createCommunity(manager);
        User outsider = user("FlagOutsider");

        assertThatThrownBy(() -> flagService.createFlag(
                community.getId(), null, FlagType.MANUAL, outsider, null, outsider))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void noteLengthAndTrimmingEnforced() {
        User manager = user("FlagMgr10");
        Community community = createCommunity(manager);

        Flag padded = flagService.createFlag(community.getId(), null, FlagType.MANUAL, manager,
                "  lead with spaces  ", manager);
        Flag loaded = reload(padded.getId());
        assertThat(loaded.getNote()).isEqualTo("lead with spaces");

        assertThatThrownBy(() -> flagService.createFlag(
                community.getId(), null, FlagType.MANUAL, manager, "x".repeat(2001), manager))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void getFlagFromAnotherCommunityThrowsResourceNotFound() {
        User managerA = user("FlagMgrA");
        Community communityA = createCommunity(managerA);
        User managerB = user("FlagMgrB");
        Community communityB = createCommunity(managerB);

        Flag created = flagService.createFlag(communityA.getId(), null, FlagType.MANUAL, managerA, "private", managerA);

        assertThatThrownBy(() -> flagService.getFlag(communityB.getId(), created.getId(), managerB))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private List<Flag> reloaded(List<Flag> flags) {
        entityManager.flush();
        entityManager.clear();
        return flags;
    }
}