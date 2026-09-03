package com.borrowbox.integration;

import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.dto.CommunityRuleRequest;
import com.borrowbox.dto.CommunityRuleResponse;
import com.borrowbox.dto.CommunityRuleUpdateRequest;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityRule;
import com.borrowbox.entity.CommunityRuleType;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.MembershipVerificationMethod;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.CommunityRuleRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityRuleService;
import com.borrowbox.service.CommunityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CommunityRuleIntegrationTest {

    @Autowired
    private CommunityRuleService communityRuleService;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityRuleRepository communityRuleRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

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
                "Rules " + UUID.randomUUID(), null, CommunityType.CLUB, null, null, null, null);
        return communityRepository.findById(communityService.createCommunity(req, manager).id()).orElseThrow();
    }

    private void addActiveMember(Community community, User member) {
        if (membershipRepository.existsByUserIdAndCommunityId(member.getId(), community.getId())) {
            return;
        }
        Membership m = new Membership();
        m.setUser(member);
        m.setCommunity(community);
        m.setRole(MembershipRole.MEMBER);
        m.setStatus(MembershipStatus.ACTIVE);
        m.setVerificationMethod(MembershipVerificationMethod.ADMIN);
        m.setVerifiedBy(member);
        m.setVerifiedAt(LocalDateTime.now());
        m.setJoinedAt(LocalDateTime.now());
        membershipRepository.save(m);
    }

    @Test
    void createRulePersistsAndReadsBackWithJsonValue() {
        User manager = user("RulesMgr");
        Community community = createCommunity(manager);

        CommunityRuleResponse created = communityRuleService.createRule(
                community.getId(),
                new CommunityRuleRequest(CommunityRuleType.MEMBERSHIP_CONTEXT_FIELDS,
                        Map.of("required", List.of("program", "year"), "section", "A")),
                manager);

        assertThat(created.status()).isEqualTo(CommunityStatus.ACTIVE);
        CommunityRule loaded = communityRuleRepository.findById(created.id()).orElseThrow();
        assertThat(loaded.getRuleType()).isEqualTo(CommunityRuleType.MEMBERSHIP_CONTEXT_FIELDS);
        assertThat(loaded.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(loaded.getValue()).containsEntry("required", List.of("program", "year"));
        assertThat(loaded.getValue()).containsEntry("section", "A");
    }

    @Test
    void createRuleArchivesExistingSameTypeKeepingSingleActive() {
        User manager = user("RulesMgr2");
        Community community = createCommunity(manager);

        CommunityRuleResponse first = communityRuleService.createRule(
                community.getId(),
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("text", "First")),
                manager);
        CommunityRuleResponse second = communityRuleService.createRule(
                community.getId(),
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("text", "Second")),
                manager);

        CommunityRule firstLoaded = communityRuleRepository.findById(first.id()).orElseThrow();
        CommunityRule secondLoaded = communityRuleRepository.findById(second.id()).orElseThrow();
        assertThat(firstLoaded.getStatus()).isEqualTo(CommunityStatus.ARCHIVED);
        assertThat(secondLoaded.getStatus()).isEqualTo(CommunityStatus.ACTIVE);

        List<CommunityRule> active = communityRuleRepository.findByCommunityIdAndStatus(
                community.getId(), CommunityStatus.ACTIVE);
        assertThat(active).hasSize(1);
    }

    @Test
    void deactivateThenActivateLifecycle() {
        User manager = user("RulesMgr3");
        Community community = createCommunity(manager);

        CommunityRuleResponse rule = communityRuleService.createRule(
                community.getId(),
                new CommunityRuleRequest(CommunityRuleType.MAX_ACTIVE_MEMBERS, Map.of("max", 200)),
                manager);

        CommunityRuleResponse deactivated = communityRuleService.deactivateRule(
                community.getId(), rule.id(), manager);
        assertThat(deactivated.status()).isEqualTo(CommunityStatus.ARCHIVED);

        CommunityRuleResponse reactivated = communityRuleService.activateRule(
                community.getId(), rule.id(), manager);
        assertThat(reactivated.status()).isEqualTo(CommunityStatus.ACTIVE);
    }

    @Test
    void updateActiveTrueArchivesConflictingActiveSameType() {
        User manager = user("RulesMgr4");
        Community community = createCommunity(manager);

        // Craft a two-ACTIVE-same-type state via direct repository writes, then
        // verify PATCH active=true archives the conflict and activates the target.
        CommunityRule activeA = new CommunityRule();
        activeA.setCommunity(community);
        activeA.setRuleType(CommunityRuleType.ADMISSION_NOTE);
        activeA.setValue(Map.of("text", "A"));
        activeA.setStatus(CommunityStatus.ACTIVE);
        activeA.setCreatedBy(manager);
        activeA.setUpdatedBy(manager);
        communityRuleRepository.saveAndFlush(activeA);

        CommunityRule activeB = new CommunityRule();
        activeB.setCommunity(community);
        activeB.setRuleType(CommunityRuleType.ADMISSION_NOTE);
        activeB.setValue(Map.of("text", "B"));
        activeB.setStatus(CommunityStatus.ACTIVE);
        activeB.setCreatedBy(manager);
        activeB.setUpdatedBy(manager);
        communityRuleRepository.saveAndFlush(activeB);

        CommunityRuleResponse updated = communityRuleService.updateRule(
                community.getId(), activeA.getId(),
                new CommunityRuleUpdateRequest(Map.of("text", "A-updated"), true),
                manager);

        assertThat(updated.status()).isEqualTo(CommunityStatus.ACTIVE);
        CommunityRule target = communityRuleRepository.findById(activeA.getId()).orElseThrow();
        CommunityRule conflict = communityRuleRepository.findById(activeB.getId()).orElseThrow();
        assertThat(target.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(target.getValue()).containsEntry("text", "A-updated");
        assertThat(conflict.getStatus()).isEqualTo(CommunityStatus.ARCHIVED);

        List<CommunityRule> active = communityRuleRepository.findByCommunityIdAndStatus(
                community.getId(), CommunityStatus.ACTIVE);
        assertThat(active).hasSize(1);
    }

    @Test
    void updateActiveFalseArchivesTarget() {
        User manager = user("RulesMgr5");
        Community community = createCommunity(manager);

        CommunityRuleResponse rule = communityRuleService.createRule(
                community.getId(),
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("text", "Draft")),
                manager);

        CommunityRuleResponse updated = communityRuleService.updateRule(
                community.getId(), rule.id(),
                new CommunityRuleUpdateRequest(Map.of("text", "Archived"), false),
                manager);

        assertThat(updated.status()).isEqualTo(CommunityStatus.ARCHIVED);
    }

    @Test
    void activeMemberCanReadActiveRules() {
        User manager = user("RulesMgr6");
        Community community = createCommunity(manager);
        communityRuleService.createRule(
                community.getId(),
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("text", "Kind")),
                manager);

        User member = user("RulesMember");
        addActiveMember(community, member);

        List<CommunityRuleResponse> active = communityRuleService.listActiveRules(community.getId(), member);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).ruleType()).isEqualTo(CommunityRuleType.ADMISSION_NOTE);
    }

    @Test
    void nonMemberCannotReadActiveRules() {
        User manager = user("RulesMgr7");
        Community community = createCommunity(manager);

        User outsider = user("RulesOutsider");

        assertThatThrownBy(() -> communityRuleService.listActiveRules(community.getId(), outsider))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void managerCanListAllRulesIncludingArchived() {
        User manager = user("RulesMgr8");
        Community community = createCommunity(manager);

        CommunityRuleResponse first = communityRuleService.createRule(
                community.getId(),
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("text", "First")),
                manager);
        communityRuleService.deactivateRule(community.getId(), first.id(), manager);
        communityRuleService.createRule(
                community.getId(),
                new CommunityRuleRequest(CommunityRuleType.MAX_ACTIVE_MEMBERS, Map.of("max", 100)),
                manager);

        List<CommunityRuleResponse> all = communityRuleService.listRulesForCommunity(community.getId(), manager);

        assertThat(all).hasSize(2);
        assertThat(all).extracting(CommunityRuleResponse::status)
                .contains(CommunityStatus.ACTIVE, CommunityStatus.ARCHIVED);
    }

    @Test
    void archivedCommunityMutationThrows() {
        User manager = user("RulesMgr9");
        Community community = createCommunity(manager);
        community.setStatus(CommunityStatus.ARCHIVED);
        communityRepository.save(community);

        assertThatThrownBy(() -> communityRuleService.createRule(
                community.getId(),
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("text", "x")),
                manager))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void readsStillWorkOnArchivedCommunityForActiveMember() {
        User manager = user("RulesMgr10");
        Community community = createCommunity(manager);
        communityRuleService.createRule(
                community.getId(),
                new CommunityRuleRequest(CommunityRuleType.ADMISSION_NOTE, Map.of("text", "Kind")),
                manager);

        community.setStatus(CommunityStatus.ARCHIVED);
        communityRepository.save(community);

        User member = user("RulesMember2");
        addActiveMember(community, member);

        List<CommunityRuleResponse> active = communityRuleService.listActiveRules(community.getId(), member);
        assertThat(active).hasSize(1);
    }
}