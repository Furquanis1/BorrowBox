package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.CommunityRuleRequest;
import com.borrowbox.dto.CommunityRuleResponse;
import com.borrowbox.dto.TransactionCreateRequest;
import com.borrowbox.dto.TransactionDecisionRequest;
import com.borrowbox.dto.TransactionResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.CommunityRule;
import com.borrowbox.entity.CommunityRuleType;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.EvidenceType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.User;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.CommunityRuleRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityRuleService;
import com.borrowbox.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V2.4.1: OVERDUE_GRACE_PERIOD rule. The grace period is stored in the
 * community_rules table as JSON {"days": N} and applied at the transaction
 * service layer when computing the derived overdue read-time condition. The
 * authoritative loan clock (startedAt / dueAt / originalDueAt) is never
 * rewritten; a missing rule, a zero value, or an invalid value means zero grace
 * and preserves V2.2.4 behavior.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CommunityRuleGracePeriodIntegrationTest {

    @Autowired
    private SeedDataInitializer seedDataInitializer;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private CommunityRuleService communityRuleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CommunityListingRepository communityListingRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CommunityRuleRepository communityRuleRepository;

    // ── Helpers ───────────────────────────────────────────────────────

    private User seedUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("missing seed user " + email));
    }

    private Asset seedAssetOf(User owner, String title) {
        return assetRepository.findByOwnerId(owner.getId()).stream()
                .filter(a -> a.getTitle().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed asset " + title));
    }

    private Long cseId() {
        return communityRepository.findAll().stream()
                .filter(c -> c.getName().equals("CSE Department"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed community CSE Department"))
                .getId();
    }

    private CommunityListing cseFootballListing(Asset football) {
        return communityListingRepository.findByAssetIdAndCommunityId(football.getId(), cseId())
                .orElseThrow(() -> new AssertionError("missing Football listing in CSE"));
    }

    private void createGraceRule(Long communityId, int days, User manager) {
        CommunityRuleResponse created = communityRuleService.createRule(
                communityId,
                new CommunityRuleRequest(CommunityRuleType.OVERDUE_GRACE_PERIOD, Map.of("days", days)),
                manager);
        assertThat(created.ruleType()).isEqualTo(CommunityRuleType.OVERDUE_GRACE_PERIOD);
    }

    private TransactionResponse activateInCse(User ahmed, User salah) {
        Asset football = seedAssetOf(ahmed, "Football");
        long listingId = cseFootballListing(football).getId();

        TransactionResponse created = transactionService.create(
                new TransactionCreateRequest(listingId, "Grace check " + UUID.randomUUID(), 3), salah);
        TransactionResponse approved = transactionService.approve(
                created.id(), new TransactionDecisionRequest("Ok"), ahmed);
        TransactionResponse staged = transactionService.stageHandover(approved.id(), salah);
        transactionService.uploadEvidence(
                staged.id(), EvidenceType.LENDER_HANDOVER,
                new MockMultipartFile("file", "handover.png", "image/png", new byte[]{2}),
                null, null, ahmed);
        return transactionService.confirmHandover(staged.id(), ahmed);
    }

    private void setDueAt(Long transactionId, LocalDateTime dueAt) {
        Transaction txn = transactionRepository.findById(transactionId).orElseThrow();
        txn.setDueAt(dueAt);
        transactionRepository.saveAndFlush(txn);
    }

    // ── Scenarios ─────────────────────────────────────────────────────

    @Test
    void overdueGracePeriodRulePersistsAndRoundTripsAsJson() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        Long cse = cseId();

        CommunityRuleResponse created = communityRuleService.createRule(
                cse,
                new CommunityRuleRequest(CommunityRuleType.OVERDUE_GRACE_PERIOD, Map.of("days", 2)),
                ahmed);

        CommunityRule loaded = communityRuleRepository.findById(created.id()).orElseThrow();
        assertThat(loaded.getRuleType()).isEqualTo(CommunityRuleType.OVERDUE_GRACE_PERIOD);
        assertThat(loaded.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(((Number) loaded.getValue().get("days")).intValue()).isEqualTo(2);
        assertThat(loaded.getValue()).containsKey("days");
    }

    @Test
    void configuredGraceDelaysOverdueWithinGraceWindow() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Long cse = cseId();
        createGraceRule(cse, 2, ahmed);

        TransactionResponse active = activateInCse(ahmed, salah);
        setDueAt(active.id(), LocalDateTime.now().minusHours(12));

        assertThat(transactionService.view(active.id(), salah).overdue()).isFalse();
        assertThat(transactionService.view(active.id(), ahmed).overdue()).isFalse();
        assertThat(transactionService.view(active.id(), salah).dueAt()).isBefore(LocalDateTime.now());
    }

    @Test
    void missingRuleMeansZeroGraceAndOverdueTrue() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");

        TransactionResponse active = activateInCse(ahmed, salah);
        setDueAt(active.id(), LocalDateTime.now().minusHours(12));

        assertThat(transactionService.view(active.id(), salah).overdue()).isTrue();
    }

    @Test
    void zeroGraceDaysRuleMeansOverdueTrue() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Long cse = cseId();
        createGraceRule(cse, 0, ahmed);

        TransactionResponse active = activateInCse(ahmed, salah);
        setDueAt(active.id(), LocalDateTime.now().minusHours(12));

        assertThat(transactionService.view(active.id(), salah).overdue()).isTrue();
    }

    @Test
    void overdueBecomesTrueAfterGracePeriodElapses() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Long cse = cseId();
        createGraceRule(cse, 1, ahmed);

        TransactionResponse active = activateInCse(ahmed, salah);
        setDueAt(active.id(), LocalDateTime.now().minusHours(36));

        assertThat(transactionService.view(active.id(), salah).overdue()).isTrue();
    }

    @Test
    void graceDoesNotAffectDueSoonOrTheLoanClock() {
        seedDataInitializer.seed();
        User ahmed = seedUser("ahmed@example.com");
        User salah = seedUser("salah@example.com");
        Long cse = cseId();
        createGraceRule(cse, 2, ahmed);

        TransactionResponse active = activateInCse(ahmed, salah);
        LocalDateTime futureDue = LocalDateTime.now().plusHours(12);
        setDueAt(active.id(), futureDue);

        assertThat(transactionService.view(active.id(), salah).dueSoon()).isTrue();
        assertThat(transactionService.view(active.id(), salah).overdue()).isFalse();
        assertThat(transactionService.view(active.id(), salah).dueAt())
                .isBetween(futureDue.minusSeconds(5), futureDue.plusSeconds(5));
    }
}