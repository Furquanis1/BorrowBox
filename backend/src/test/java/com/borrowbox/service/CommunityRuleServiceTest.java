package com.borrowbox.service;

import com.borrowbox.dto.CommunityRuleRequest;
import com.borrowbox.dto.CommunityRuleResponse;
import com.borrowbox.dto.CommunityRuleUpdateRequest;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityRule;
import com.borrowbox.entity.CommunityRuleType;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.CommunityRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CommunityRuleServiceTest {

    @Mock
    private CommunityRuleRepository communityRuleRepository;

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private MembershipService membershipService;

    private CommunityRuleService service;

    @BeforeEach
    void setUp() {
        service = new CommunityRuleService(communityRuleRepository, communityRepository, membershipService);
    }

    private User manager(Long id) {
        User u = new User("Mgr" + id, "mgr" + id + "@example.com");
        u.setId(id);
        return u;
    }

    private Community community(Long id, CommunityStatus status) {
        Community c = new Community();
        c.setId(id);
        c.setName("C" + id);
        c.setType(CommunityType.CLUB);
        c.setStatus(status);
        return c;
    }

    private CommunityRule rule(Long id, Long communityId, CommunityRuleType type, CommunityStatus status) {
        CommunityRule r = new CommunityRule();
        r.setId(id);
        r.setCommunity(community(communityId, CommunityStatus.ACTIVE));
        r.setRuleType(type);
        r.setStatus(status);
        return r;
    }

    // ─── Locking + create/upsert ─────────────────────────────────────────

    @Test
    void createRuleLocksCommunityBeforeWrite() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        when(communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE))
                .thenReturn(List.of());
        when(communityRuleRepository.save(any(CommunityRule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("text", "Be kind")),
                manager(1L));

        verify(communityRepository).findByIdForUpdate(10L);
    }

    @Test
    void createRuleOnEmptyInsertsActiveRule() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        when(communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                10L, CommunityRuleType.MEMBERSHIP_CONTEXT_FIELDS, CommunityStatus.ACTIVE))
                .thenReturn(List.of());
        when(communityRuleRepository.save(any(CommunityRule.class))).thenAnswer(inv -> inv.getArgument(0));

        CommunityRuleResponse resp = service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.MEMBERSHIP_CONTEXT_FIELDS,
                        Map.of("fields", List.of("program", "year"))),
                manager(1L));

        assertThat(resp.ruleType()).isEqualTo(CommunityRuleType.MEMBERSHIP_CONTEXT_FIELDS);
        assertThat(resp.status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(resp.value()).containsEntry("fields", List.of("program", "year"));
        assertThat(resp.value()).doesNotContainKey("required");
    }

    @Test
    void createRuleNormalizesLegacyMembershipRequiredKey() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        when(communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                10L, CommunityRuleType.MEMBERSHIP_CONTEXT_FIELDS, CommunityStatus.ACTIVE))
                .thenReturn(List.of());
        when(communityRuleRepository.save(any(CommunityRule.class))).thenAnswer(inv -> inv.getArgument(0));

        CommunityRuleResponse resp = service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.MEMBERSHIP_CONTEXT_FIELDS,
                        Map.of("required", List.of("section", "tower"))),
                manager(1L));

        assertThat(resp.value()).containsEntry("fields", List.of("section", "tower"));
        assertThat(resp.value()).doesNotContainKey("required");
    }

    @Test
    void createRuleArchivesExistingActiveSameTypeBeforeInsert() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        CommunityRule existing = rule(50L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        when(communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE))
                .thenReturn(List.of(existing));
        when(communityRuleRepository.save(any(CommunityRule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("text", "New")),
                manager(1L));

        assertThat(existing.getStatus()).isEqualTo(CommunityStatus.ARCHIVED);
        verify(communityRuleRepository).save(existing);
    }

    @Test
    void createRuleOnMissingCommunityThrowsNotFound() {
        when(communityRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRule(99L,
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of()),
                manager(1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createRuleOnArchivedCommunityThrows() {
        Community c = community(10L, CommunityStatus.ARCHIVED);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of()),
                manager(1L)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void nonManagerCannotCreateRule() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(2L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of()),
                manager(2L)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─── PATCH/update ─────────────────────────────────────────────────────

    @Test
    void updateRuleActiveTrueLocksAndArchivesConflictingThenActivates() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        CommunityRule target = rule(51L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ARCHIVED);
        when(communityRuleRepository.findById(51L)).thenReturn(Optional.of(target));
        CommunityRule conflicting = rule(52L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        when(communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE))
                .thenReturn(List.of(conflicting));
        when(communityRuleRepository.save(any(CommunityRule.class))).thenAnswer(inv -> inv.getArgument(0));

        CommunityRuleResponse resp = service.updateRule(10L, 51L,
                new CommunityRuleUpdateRequest(Map.of("note", "Updated"), true),
                manager(1L));

        verify(communityRepository).findByIdForUpdate(10L);
        assertThat(conflicting.getStatus()).isEqualTo(CommunityStatus.ARCHIVED);
        assertThat(target.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(resp.status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(resp.value()).containsEntry("note", "Updated");
    }

    @Test
    void updateRuleActiveFalseArchivesTarget() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        CommunityRule target = rule(51L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        when(communityRuleRepository.findById(51L)).thenReturn(Optional.of(target));
        when(communityRuleRepository.save(any(CommunityRule.class))).thenAnswer(inv -> inv.getArgument(0));

        CommunityRuleResponse resp = service.updateRule(10L, 51L,
                new CommunityRuleUpdateRequest(Map.of("note", "Draft"), false),
                manager(1L));

        assertThat(target.getStatus()).isEqualTo(CommunityStatus.ARCHIVED);
        assertThat(resp.status()).isEqualTo(CommunityStatus.ARCHIVED);
        assertThat(resp.value()).containsEntry("note", "Draft");
    }

    // ─── Validation ─────────────────────────────────────────────────────

    private void validCreateSetup() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
    }

    private void stubRuleSave() {
        when(communityRuleRepository.save(any(CommunityRule.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void expectValidationFailure(CommunityRuleType type, Map<String, Object> value) {
        validCreateSetup();
        assertThatThrownBy(() -> service.createRule(10L, new CommunityRuleRequest(type, value), manager(1L)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining(type.name());
        verify(communityRuleRepository, org.mockito.Mockito.never()).save(any(CommunityRule.class));
    }

    @Test
    void createRuleNormalizesLegacyAdmissionNoteTextKey() {
        validCreateSetup();
        stubRuleSave();
        when(communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE)).thenReturn(List.of());

        CommunityRuleResponse resp = service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("text", "Legacy")),
                manager(1L));

        assertThat(resp.value()).containsEntry("note", "Legacy");
        assertThat(resp.value()).doesNotContainKey("text");
    }

    @Test
    void admissionNoteRejectsMissingNote() {
        validCreateSetup();
        assertThatThrownBy(() -> service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of()), manager(1L)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("note");
    }

    @Test
    void admissionNoteRejectsBlankNote() {
        validCreateSetup();
        assertThatThrownBy(() -> service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("note", "   ")), manager(1L)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void admissionNoteRejectsOverlongNote() {
        expectValidationFailure(CommunityRuleType.ADMISSION_NOTE,
                Map.of("note", "x".repeat(2001)));
    }

    @Test
    void maxActiveMembersAcceptsOne() {
        validCreateSetup();
        stubRuleSave();
        when(communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                10L, CommunityRuleType.MAX_ACTIVE_MEMBERS, CommunityStatus.ACTIVE)).thenReturn(List.of());

        CommunityRuleResponse resp = service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.MAX_ACTIVE_MEMBERS, Map.of("max", 1)),
                manager(1L));

        assertThat(resp.value()).containsEntry("max", 1L);
    }

    @Test
    void maxActiveMembersRejectsZero() {
        validCreateSetup();
        assertThatThrownBy(() -> service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.MAX_ACTIVE_MEMBERS, Map.of("max", 0)), manager(1L)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void maxActiveMembersRejectsNegative() {
        expectValidationFailure(CommunityRuleType.MAX_ACTIVE_MEMBERS, Map.of("max", -5));
    }

    @Test
    void maxActiveMembersRejectsNonNumeric() {
        validCreateSetup();
        assertThatThrownBy(() -> service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.MAX_ACTIVE_MEMBERS, Map.of("max", "many")), manager(1L)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void overdueGracePeriodAcceptsZeroAndThirty() {
        validCreateSetup();
        stubRuleSave();
        when(communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                10L, CommunityRuleType.OVERDUE_GRACE_PERIOD, CommunityStatus.ACTIVE)).thenReturn(List.of());

        CommunityRuleResponse zero = service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.OVERDUE_GRACE_PERIOD, Map.of("days", 0)),
                manager(1L));
        CommunityRuleResponse thirty = service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.OVERDUE_GRACE_PERIOD, Map.of("days", 30)),
                manager(1L));

        assertThat(zero.value()).containsEntry("days", 0L);
        assertThat(thirty.value()).containsEntry("days", 30L);
    }

    @Test
    void overdueGracePeriodRejectsNegative() {
        expectValidationFailure(CommunityRuleType.OVERDUE_GRACE_PERIOD, Map.of("days", -1));
    }

    @Test
    void overdueGracePeriodRejectsThirtyOne() {
        expectValidationFailure(CommunityRuleType.OVERDUE_GRACE_PERIOD, Map.of("days", 31));
    }

    @Test
    void overdueGracePeriodRejectsNonNumeric() {
        validCreateSetup();
        assertThatThrownBy(() -> service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.OVERDUE_GRACE_PERIOD, Map.of("days", "soon")), manager(1L)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void membershipContextFieldsRejectsUnknownField() {
        expectValidationFailure(CommunityRuleType.MEMBERSHIP_CONTEXT_FIELDS,
                Map.of("fields", List.of("program", "favorite_color")));
    }

    @Test
    void membershipContextFieldsRejectsEmptyList() {
        expectValidationFailure(CommunityRuleType.MEMBERSHIP_CONTEXT_FIELDS,
                Map.of("fields", List.of()));
    }

    @Test
    void membershipContextFieldsRejectsMissingValue() {
        validCreateSetup();
        assertThatThrownBy(() -> service.createRule(10L,
                new CommunityRuleRequest(CommunityRuleType.MEMBERSHIP_CONTEXT_FIELDS, Map.of()), manager(1L)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void updateRuleNotInGivenCommunityThrows() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        CommunityRule other = rule(60L, 99L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        when(communityRuleRepository.findById(60L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateRule(10L, 60L,
                new CommunityRuleUpdateRequest(Map.of(), false), manager(1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMissingRuleThrowsNotFound() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        when(communityRuleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRule(10L, 99L,
                new CommunityRuleUpdateRequest(Map.of(), false), manager(1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Activate / deactivate ────────────────────────────────────────────

    @Test
    void activateRuleLocksCommunityAndArchivesConflicting() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        CommunityRule target = rule(51L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ARCHIVED);
        when(communityRuleRepository.findById(51L)).thenReturn(Optional.of(target));
        CommunityRule conflicting = rule(52L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        when(communityRuleRepository.findByCommunityIdAndRuleTypeAndStatus(
                10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE))
                .thenReturn(List.of(conflicting));
        when(communityRuleRepository.save(any(CommunityRule.class))).thenAnswer(inv -> inv.getArgument(0));

        CommunityRuleResponse resp = service.activateRule(10L, 51L, manager(1L));

        verify(communityRepository).findByIdForUpdate(10L);
        assertThat(conflicting.getStatus()).isEqualTo(CommunityStatus.ARCHIVED);
        assertThat(target.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(resp.status()).isEqualTo(CommunityStatus.ACTIVE);
    }

    @Test
    void deactivateRuleArchivesTargetUnderCommunityLock() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        CommunityRule target = rule(51L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE);
        when(communityRuleRepository.findById(51L)).thenReturn(Optional.of(target));
        when(communityRuleRepository.save(any(CommunityRule.class))).thenAnswer(inv -> inv.getArgument(0));

        CommunityRuleResponse resp = service.deactivateRule(10L, 51L, manager(1L));

        verify(communityRepository).findByIdForUpdate(10L);
        assertThat(target.getStatus()).isEqualTo(CommunityStatus.ARCHIVED);
        assertThat(resp.status()).isEqualTo(CommunityStatus.ARCHIVED);
    }

    @Test
    void archivedCommunityCannotBeMutatedAnyWhere() {
        Community c = community(10L, CommunityStatus.ARCHIVED);
        when(communityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivateRule(10L, 51L, manager(1L)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ─── Reads ────────────────────────────────────────────────────────────

    @Test
    void managerCanListAllRules() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findById(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(1L, 10L)).thenReturn(true);
        when(communityRuleRepository.findByCommunityId(10L)).thenReturn(List.of(
                rule(51L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE),
                rule(52L, 10L, CommunityRuleType.MAX_ACTIVE_MEMBERS, CommunityStatus.ARCHIVED)));

        List<CommunityRuleResponse> rules = service.listRulesForCommunity(10L, manager(1L));

        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).ruleType()).isEqualTo(CommunityRuleType.ADMISSION_NOTE);
        assertThat(rules.get(1).ruleType()).isEqualTo(CommunityRuleType.MAX_ACTIVE_MEMBERS);
    }

    @Test
    void nonManagerCannotListAllRules() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findById(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveManager(2L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.listRulesForCommunity(10L, manager(2L)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void activeMemberCanListActiveRules() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findById(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveMember(1L, 10L)).thenReturn(true);
        when(communityRuleRepository.findByCommunityIdAndStatus(10L, CommunityStatus.ACTIVE))
                .thenReturn(List.of(rule(51L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE)));

        List<CommunityRuleResponse> rules = service.listActiveRules(10L, manager(1L));

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).status()).isEqualTo(CommunityStatus.ACTIVE);
    }

    @Test
    void nonMemberCannotListActiveRules() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findById(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveMember(2L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.listActiveRules(10L, manager(2L)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void nullUserCannotListActiveRules() {
        Community c = community(10L, CommunityStatus.ACTIVE);
        when(communityRepository.findById(10L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.listActiveRules(10L, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void readsWorkOnArchivedCommunityForAuthorizedMember() {
        Community c = community(10L, CommunityStatus.ARCHIVED);
        when(communityRepository.findById(10L)).thenReturn(Optional.of(c));
        when(membershipService.isActiveMember(1L, 10L)).thenReturn(true);
        when(communityRuleRepository.findByCommunityIdAndStatus(10L, CommunityStatus.ACTIVE))
                .thenReturn(List.of(rule(51L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE)));

        List<CommunityRuleResponse> rules = service.listActiveRules(10L, manager(1L));

        assertThat(rules).hasSize(1);
    }

    @Test
    void activeAdmissionRulesIsNoOpIntegrationHook() {
        when(communityRuleRepository.findByCommunityIdAndStatus(10L, CommunityStatus.ACTIVE))
                .thenReturn(List.of(rule(51L, 10L, CommunityRuleType.ADMISSION_NOTE, CommunityStatus.ACTIVE)));

        List<CommunityRule> rules = service.activeAdmissionRules(10L);

        assertThat(rules).hasSize(1);
        verify(communityRuleRepository).findByCommunityIdAndStatus(eq(10L), any());
        verify(communityRuleRepository, org.mockito.Mockito.never())
                .findByCommunityIdAndRuleTypeAndStatus(
                        eq(10L), any(CommunityRuleType.class), any(CommunityStatus.class));
    }
}