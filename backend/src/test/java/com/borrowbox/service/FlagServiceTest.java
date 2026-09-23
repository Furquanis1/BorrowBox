package com.borrowbox.service;

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

    private FlagService service;

    @BeforeEach
    void setUp() {
        service = new FlagService(flagRepository, communityRepository, membershipService);
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
}