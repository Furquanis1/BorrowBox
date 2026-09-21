package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.ReputationEventResponse;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.ReputationEventType;
import com.borrowbox.entity.ReputationRole;
import com.borrowbox.entity.User;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.ReputationEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Quick integration test to verify ReputationEventService membership check.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ReputationEventServiceIntegrationTest {

    @Autowired private SeedDataInitializer seedDataInitializer;
    @Autowired private ReputationEventService reputationEventService;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;

    @Test
    void reputationEventsForKarimInEngineeringOffice() {
        seedDataInitializer.seed();

        // Find Karim and Engineering Office community
        var karim = userRepository.findByEmail("karim@example.com").orElseThrow();

        // Get all reputation events for Karim in Engineering Office (community_id=3)
        var events = reputationEventService.listForUser(karim.getId(), 3L);

        // Should have at least one reputation event
        assertThat(events).isNotEmpty();

        // Should have the borrower LOAN_COMPLETED event
        var borrowerEvents = events.stream()
            .filter(e -> e.role() == ReputationRole.BORROWER
                && e.eventType() == ReputationEventType.LOAN_COMPLETED)
            .toList();
        assertThat(borrowerEvents).hasSizeGreaterThanOrEqualTo(1);

        var karimEvent = borrowerEvents.get(0);
        assertThat(karimEvent.successful()).isTrue();
        assertThat(karimEvent.onTime()).isTrue();
        assertThat(karimEvent.communityId()).isEqualTo(3);
    }

    @Test
    void reputationEventsForAhmedInEngineeringOffice() {
        seedDataInitializer.seed();

        var ahmed = userRepository.findByEmail("ahmed@example.com").orElseThrow();

        var events = reputationEventService.listForUser(ahmed.getId(), 3L);

        var lenderEvents = events.stream()
            .filter(e -> e.role() == ReputationRole.LENDER
                && e.eventType() == ReputationEventType.LOAN_COMPLETED)
            .toList();
        assertThat(lenderEvents).hasSizeGreaterThanOrEqualTo(1);

        var ahmedEvent = lenderEvents.get(0);
        assertThat(ahmedEvent.successful()).isTrue();
        assertThat(ahmedEvent.onTime()).isNull();
        assertThat(ahmedEvent.communityId()).isEqualTo(3);
    }

    @Test
    void reputationEventsForOmarInHostelBlockB() {
        seedDataInitializer.seed();

        var omar = userRepository.findByEmail("omar@example.com").orElseThrow();

        var events = reputationEventService.listForUser(omar.getId(), 2L); // Hostel Block B

        var borrowerEvents = events.stream()
            .filter(e -> e.role() == ReputationRole.BORROWER
                && e.eventType() == ReputationEventType.LOAN_COMPLETED)
            .toList();
        assertThat(borrowerEvents).hasSizeGreaterThanOrEqualTo(1);

        var omarEvent = borrowerEvents.get(0);
        assertThat(omarEvent.successful()).isTrue();
        assertThat(omarEvent.onTime()).isFalse();
        assertThat(omarEvent.communityId()).isEqualTo(2);
    }

    @Test
    void reputationEventsForYoussefInHostelBlockB() {
        seedDataInitializer.seed();

        var youssef = userRepository.findByEmail("youssef@example.com").orElseThrow();

        var events = reputationEventService.listForUser(youssef.getId(), 2L);

        var lenderEvents = events.stream()
            .filter(e -> e.role() == ReputationRole.LENDER
                && e.eventType() == ReputationEventType.LOAN_COMPLETED)
            .toList();
        assertThat(lenderEvents).hasSizeGreaterThanOrEqualTo(1);

        var youssefEvent = lenderEvents.get(0);
        assertThat(youssefEvent.successful()).isTrue();
        assertThat(youssefEvent.onTime()).isNull();
        assertThat(youssefEvent.communityId()).isEqualTo(2);
    }

    @Test
    void nonMemberScopeReturns403() {
        seedDataInitializer.seed();

        // Salah is only a member of CSE Department
        var salah = userRepository.findByEmail("salah@example.com").orElseThrow();

        // Engineering Office (community_id=3) - Salah is NOT a member
        assertThatThrownBy(() -> reputationEventService.listForUser(salah.getId(), 3L))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("not an active member");

        // Hostel Block B (community_id=2) - Salah is NOT a member
        assertThatThrownBy(() -> reputationEventService.listForUser(salah.getId(), 2L))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("not an active member");
    }
}