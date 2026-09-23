package com.borrowbox.service;

import com.borrowbox.dto.FlagUpdateRequest;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.Flag;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.FlagRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FlagServiceTest {

    @Mock
    private FlagRepository flagRepository;

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private MembershipService membershipService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    private FlagService service;

    @BeforeEach
    void setUp() {
        service = new FlagService(flagRepository, communityRepository, membershipService,
                transactionRepository, userRepository);
    }

    private User user(Long id) {
        User u = new User("U" + id, "u" + id + "@example.com");
        u.setId(id);
        return u;
    }

    private Community community(Long id) {
        Community c = new Community();
        c.setId(id);
        c.setName("C" + id);
        c.setType(CommunityType.CLUB);
        return c;
    }

    private Transaction transactionOf(Long id, Community community) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setCommunity(community);
        return t;
    }

    private Flag flag(Long id, Long communityId, FlagStatus status, FlagType type) {
        Flag f = new Flag();
        f.setId(id);
        f.setCommunity(community(communityId));
        f.setStatus(status);
        f.setFlagType(type);
        return f;
    }

    private void stubActiveManager(User manager, Long communityId) {
        when(membershipService.isActiveManager(manager.getId(), communityId)).thenReturn(true);
    }

    private void stubCommunity(Long communityId) {
        when(communityRepository.findById(communityId)).thenReturn(Optional.of(community(communityId)));
    }

    // ─── Creation ──────────────────────────────────────────────────────

    @Test
    void createFlagSavesCoreFactsAndDefaultsToOpen() {
        User manager = user(100L);
        User reporter = user(101L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flag created = service.createFlag(900L, null, FlagType.MANUAL, reporter, "  broken shelf  ", manager);

        assertThat(created.getStatus()).isEqualTo(FlagStatus.OPEN);
        assertThat(created.getFlagType()).isEqualTo(FlagType.MANUAL);
        assertThat(created.getReporter().getId()).isEqualTo(101L);
        assertThat(created.getCommunity().getId()).isEqualTo(900L);
        assertThat(created.getNote()).isEqualTo("broken shelf");
        assertThat(created.getOccurredAt()).isNotNull();
        verify(flagRepository).save(created);
    }

    @Test
    void createFlagAllowsNullReporterForSystemStyleFlags() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flag created = service.createFlag(900L, null, FlagType.OVERDUE, null, null, manager);

        assertThat(created.getReporter()).isNull();
        assertThat(created.getFlagType()).isEqualTo(FlagType.OVERDUE);
        assertThat(created.getNote()).isNull();
    }

    @Test
    void createFlagRejectsNullFlagType() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);

        assertThatThrownBy(() -> service.createFlag(900L, null, null, null, null, manager))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void createFlagRejectsTransactionFromAnotherCommunity() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        Transaction foreign = transactionOf(50L, community(999L));

        assertThatThrownBy(() -> service.createFlag(900L, foreign, FlagType.MANUAL, null, null, manager))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void createFlagRequiresActiveManager() {
        User outsider = user(100L);
        stubCommunity(900L);

        assertThatThrownBy(() -> service.createFlag(900L, null, FlagType.MANUAL, null, null, outsider))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─── Workflow mutations ────────────────────────────────────────────

    @Test
    void updateStatusMovesWorkflowField() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        when(flagRepository.findById(11L)).thenReturn(Optional.of(flag(11L, 900L, FlagStatus.OPEN, FlagType.MANUAL)));
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flag reviewed = service.updateStatus(900L, 11L, FlagStatus.REVIEWED, manager);
        assertThat(reviewed.getStatus()).isEqualTo(FlagStatus.REVIEWED);

        Flag resolved = service.updateStatus(900L, 11L, FlagStatus.RESOLVED, manager);
        assertThat(resolved.getStatus()).isEqualTo(FlagStatus.RESOLVED);

        Flag dismissed = service.updateStatus(900L, 11L, FlagStatus.DISMISSED, manager);
        assertThat(dismissed.getStatus()).isEqualTo(FlagStatus.DISMISSED);
    }

    @Test
    void updateStatusRejectsNullStatus() {
        User manager = user(100L);

        assertThatThrownBy(() -> service.updateStatus(900L, 11L, null, manager))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void assignThenUnassignAssignee() {
        User manager = user(100L);
        User assignee = user(200L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        when(membershipService.isActiveManager(assignee.getId(), 900L)).thenReturn(true);
        when(flagRepository.findById(11L)).thenReturn(Optional.of(flag(11L, 900L, FlagStatus.OPEN, FlagType.MANUAL)));
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flag assigned = service.assignAssignee(900L, 11L, assignee, manager);
        assertThat(assigned.getAssignee().getId()).isEqualTo(200L);

        Flag unassigned = service.assignAssignee(900L, 11L, null, manager);
        assertThat(unassigned.getAssignee()).isNull();
    }

    @Test
    void assignAssigneeRejectsNonManagerAssignee() {
        User manager = user(100L);
        User member = user(300L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);

        assertThatThrownBy(() -> service.assignAssignee(900L, 11L, member, manager))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void updateNoteTrimsAndEnforcesMaxLength() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        when(flagRepository.findById(11L)).thenReturn(Optional.of(flag(11L, 900L, FlagStatus.OPEN, FlagType.MANUAL)));
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flag updated = service.updateNote(900L, 11L, "  needs follow-up  ", manager);
        assertThat(updated.getNote()).isEqualTo("needs follow-up");

        assertThatThrownBy(() -> service.updateNote(900L, 11L, "x".repeat(2001), manager))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ─── Queries + immutability ────────────────────────────────────────

    @Test
    void coreIncidentFactsStayImmutableAcrossWorkflowMutations() {
        User manager = user(100L);
        User reporter = user(101L);
        Community community = community(900L);
        Transaction transaction = transactionOf(50L, community);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        AtomicReference<Flag> holder = new AtomicReference<>();
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> {
            holder.set(invocation.getArgument(0));
            return holder.get();
        });
        when(flagRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(holder.get()));

        service.createFlag(900L, transaction, FlagType.RETURN_DISPUTED, reporter, "note", manager);
        service.updateStatus(900L, holder.get().getId(), FlagStatus.REVIEWED, manager);
        service.updateNote(900L, holder.get().getId(), "other", manager);

        Flag mutated = holder.get();
        assertThat(mutated.getStatus()).isEqualTo(FlagStatus.REVIEWED);
        assertThat(mutated.getCommunity().getId()).isEqualTo(900L);
        assertThat(mutated.getTransaction().getId()).isEqualTo(50L);
        assertThat(mutated.getFlagType()).isEqualTo(FlagType.RETURN_DISPUTED);
        assertThat(mutated.getReporter().getId()).isEqualTo(101L);
        assertThat(mutated.getOccurredAt()).isNotNull();
    }

    @Test
    void listAndGetQueriesRouteThroughRepository() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        Flag f1 = flag(1L, 900L, FlagStatus.OPEN, FlagType.MANUAL);
        Flag f2 = flag(2L, 900L, FlagStatus.REVIEWED, FlagType.OVERDUE);
        when(flagRepository.findByCommunityId(900L)).thenReturn(List.of(f1, f2));
        when(flagRepository.findByCommunityIdAndStatus(900L, FlagStatus.OPEN)).thenReturn(List.of(f1));
        when(flagRepository.findByCommunityIdAndFlagType(900L, FlagType.OVERDUE)).thenReturn(List.of(f2));
        when(flagRepository.findByCommunityIdAndTransactionId(900L, 50L)).thenReturn(List.of(f1));

        assertThat(service.listByCommunity(900L, manager)).containsExactly(f1, f2);
        assertThat(service.listByCommunityAndStatus(900L, FlagStatus.OPEN, manager)).containsExactly(f1);
        assertThat(service.listByCommunityAndFlagType(900L, FlagType.OVERDUE, manager)).containsExactly(f2);
        assertThat(service.listByTransaction(900L, 50L, manager)).containsExactly(f1);
    }

    @Test
    void getFlagRejectsFlagOwnedByAnotherCommunity() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        when(flagRepository.findById(11L)).thenReturn(Optional.of(flag(11L, 999L, FlagStatus.OPEN, FlagType.MANUAL)));

        assertThatThrownBy(() -> service.getFlag(900L, 11L, manager))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── V2.4.2: transaction-id create ──────────────────────────────────────

    @Test
    void createByIdResolvesTransactionAndReportsTheManager() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        Transaction txn = transactionOf(50L, community(900L));
        when(transactionRepository.findById(50L)).thenReturn(Optional.of(txn));
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flag created = service.createFlag(900L, 50L, FlagType.OVERDUE, null, manager);

        assertThat(created.getTransaction().getId()).isEqualTo(50L);
        assertThat(created.getReporter().getId()).isEqualTo(100L);
        assertThat(created.getFlagType()).isEqualTo(FlagType.OVERDUE);
    }

    @Test
    void createByIdThrowsWhenTransactionMissing() {
        User manager = user(100L);

        assertThatThrownBy(() -> service.createFlag(900L, 50L, FlagType.MANUAL, null, manager))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createByIdRejectsTransactionFromAnotherCommunity() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        when(transactionRepository.findById(50L))
                .thenReturn(Optional.of(transactionOf(50L, community(999L))));

        assertThatThrownBy(() -> service.createFlag(900L, 50L, FlagType.MANUAL, null, manager))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ─── V2.4.2: consolidated PATCH update ──────────────────────────────────

    @Test
    void updateFlagAppliesOnlyPresentFields() {
        User manager = user(100L);
        User assignee = user(200L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        when(membershipService.isActiveManager(200L, 900L)).thenReturn(true);
        when(userRepository.findById(200L)).thenReturn(Optional.of(assignee));
        Flag target = flag(11L, 900L, FlagStatus.OPEN, FlagType.MANUAL);
        when(flagRepository.findById(11L)).thenReturn(Optional.of(target));
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flag updated = service.updateFlag(900L, 11L,
                new FlagUpdateRequest(FlagStatus.REVIEWED, 200L, false, "  checking  "), manager);

        assertThat(updated.getStatus()).isEqualTo(FlagStatus.REVIEWED);
        assertThat(updated.getAssignee().getId()).isEqualTo(200L);
        assertThat(updated.getNote()).isEqualTo("checking");
    }

    @Test
    void updateFlagClearAssigneeUnassigns() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        Flag target = flag(11L, 900L, FlagStatus.OPEN, FlagType.MANUAL);
        target.setAssignee(user(200L));
        when(flagRepository.findById(11L)).thenReturn(Optional.of(target));
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flag updated = service.updateFlag(900L, 11L,
                new FlagUpdateRequest(null, null, true, null), manager);

        assertThat(updated.getAssignee()).isNull();
    }

    @Test
    void updateFlagRejectsAssignAndUnassignTogether() {
        User manager = user(100L);

        assertThatThrownBy(() -> service.updateFlag(900L, 11L,
                new FlagUpdateRequest(null, 200L, true, null), manager))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void updateFlagRejectsNonManagerAssignee() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        when(userRepository.findById(300L)).thenReturn(Optional.of(user(300L)));
        when(flagRepository.findById(11L)).thenReturn(Optional.of(flag(11L, 900L, FlagStatus.OPEN, FlagType.MANUAL)));

        assertThatThrownBy(() -> service.updateFlag(900L, 11L,
                new FlagUpdateRequest(null, 300L, false, null), manager))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void updateFlagRequiresActiveManagerAndOwningCommunity() {
        User outsider = user(100L);
        stubCommunity(900L);

        assertThatThrownBy(() -> service.updateFlag(900L, 11L,
                new FlagUpdateRequest(FlagStatus.RESOLVED, null, false, null), outsider))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─── V2.4.2: combined filters ───────────────────────────────────────────

    @Test
    void listFilteredDelegatesToCombinedQueryWhenAnyFilterPresent() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        Flag f = flag(1L, 900L, FlagStatus.OPEN, FlagType.OVERDUE);
        when(flagRepository.findFiltered(900L, FlagStatus.OPEN, FlagType.OVERDUE, 50L))
                .thenReturn(List.of(f));

        List<Flag> result = service.listFiltered(900L, FlagStatus.OPEN, FlagType.OVERDUE, 50L, manager);

        assertThat(result).containsExactly(f);
        verify(flagRepository).findFiltered(eq(900L), eq(FlagStatus.OPEN), eq(FlagType.OVERDUE), eq(50L));
    }

    @Test
    void listFilteredWithoutFiltersReturnsFullList() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        Flag f1 = flag(1L, 900L, FlagStatus.OPEN, FlagType.MANUAL);
        when(flagRepository.findFiltered(900L, null, null, null)).thenReturn(List.of(f1));

        List<Flag> result = service.listFiltered(900L, null, null, null, manager);

        assertThat(result).containsExactly(f1);
        verify(flagRepository).findFiltered(eq(900L), isNull(), isNull(), isNull());
    }

    @Test
    void listFilteredRequiresManager() {
        User outsider = user(100L);
        stubCommunity(900L);

        assertThatThrownBy(() -> service.listFiltered(900L, FlagStatus.OPEN, null, null, outsider))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void recentFlagsAndOpenCountDelegateToRepository() {
        User manager = user(100L);
        stubCommunity(900L);
        stubActiveManager(manager, 900L);
        Flag f = flag(1L, 900L, FlagStatus.OPEN, FlagType.OVERDUE);
        when(flagRepository.findTop10ByCommunityIdOrderByOccurredAtDescIdDesc(900L)).thenReturn(List.of(f));
        when(flagRepository.countByCommunityIdAndStatus(900L, FlagStatus.OPEN)).thenReturn(3L);

        assertThat(service.recentFlags(900L, manager)).containsExactly(f);
        assertThat(service.countOpen(900L)).isEqualTo(3L);
    }
}
