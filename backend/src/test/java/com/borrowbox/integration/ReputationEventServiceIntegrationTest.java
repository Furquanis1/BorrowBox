package com.borrowbox.integration;

import com.borrowbox.config.SeedDataInitializer;
import com.borrowbox.dto.ReputationEventResponse;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.ReputationEventType;
import com.borrowbox.entity.ReputationRole;
import com.borrowbox.entity.User;
import com.borrowbox.repository.CommunityRepository;
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
    @Autowired private CommunityRepository communityRepository;

    private Community seedCommunity(String name) {
        return communityRepository.findAll().stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing seed community " + name));
    }

    @Test
    void reputationEventsForKarimInEngineeringOffice() {
        seedDataInitializer.seed();

        var karim = userRepository.findByEmail("karim@example.com").orElseThrow();
        var office = seedCommunity("Engineering Office");

        var events = reputationEventService.listForUser(karim.getId(), office.getId());

        assertThat(events).isNotEmpty();

        var borrowerEvents = events.stream()
            .filter(e -> e.role() == ReputationRole.BORROWER
                && e.eventType() == ReputationEventType.LOAN_COMPLETED)
            .toList();
        assertThat(borrowerEvents).hasSizeGreaterThanOrEqualTo(1);

        var karimEvent = borrowerEvents.get(0);
        assertThat(karimEvent.successful()).isTrue();
        assertThat(karimEvent.onTime()).isTrue();
        assertThat(karimEvent.communityId()).isEqualTo(office.getId());
    }

    @Test
    void reputationEventsForAhmedInEngineeringOffice() {
        seedDataInitializer.seed();

        var ahmed = userRepository.findByEmail("ahmed@example.com").orElseThrow();
        var office = seedCommunity("Engineering Office");

        var events = reputationEventService.listForUser(ahmed.getId(), office.getId());

        var lenderEvents = events.stream()
            .filter(e -> e.role() == ReputationRole.LENDER
                && e.eventType() == ReputationEventType.LOAN_COMPLETED)
            .toList();
        assertThat(lenderEvents).hasSizeGreaterThanOrEqualTo(1);

        var ahmedEvent = lenderEvents.get(0);
        assertThat(ahmedEvent.successful()).isTrue();
        assertThat(ahmedEvent.onTime()).isNull();
        assertThat(ahmedEvent.communityId()).isEqualTo(office.getId());
    }

    @Test
    void reputationEventsForOmarInHostelBlockB() {
        seedDataInitializer.seed();

        var omar = userRepository.findByEmail("omar@example.com").orElseThrow();
        var hostel = seedCommunity("Hostel Block B");

        var events = reputationEventService.listForUser(omar.getId(), hostel.getId());

        var borrowerEvents = events.stream()
            .filter(e -> e.role() == ReputationRole.BORROWER
                && e.eventType() == ReputationEventType.LOAN_COMPLETED)
            .toList();
        assertThat(borrowerEvents).hasSizeGreaterThanOrEqualTo(1);

        var omarEvent = borrowerEvents.get(0);
        assertThat(omarEvent.successful()).isTrue();
        assertThat(omarEvent.onTime()).isFalse();
        assertThat(omarEvent.communityId()).isEqualTo(hostel.getId());
    }

    @Test
    void reputationEventsForYoussefInHostelBlockB() {
        seedDataInitializer.seed();

        var youssef = userRepository.findByEmail("youssef@example.com").orElseThrow();
        var hostel = seedCommunity("Hostel Block B");

        var events = reputationEventService.listForUser(youssef.getId(), hostel.getId());

        var lenderEvents = events.stream()
            .filter(e -> e.role() == ReputationRole.LENDER
                && e.eventType() == ReputationEventType.LOAN_COMPLETED)
            .toList();
        assertThat(lenderEvents).hasSizeGreaterThanOrEqualTo(1);

        var youssefEvent = lenderEvents.get(0);
        assertThat(youssefEvent.successful()).isTrue();
        assertThat(youssefEvent.onTime()).isNull();
        assertThat(youssefEvent.communityId()).isEqualTo(hostel.getId());
    }

    @Test
    void nonMemberScopeReturns403() {
        seedDataInitializer.seed();

        var salah = userRepository.findByEmail("salah@example.com").orElseThrow();
        var office = seedCommunity("Engineering Office");
        var hostel = seedCommunity("Hostel Block B");

        assertThatThrownBy(() -> reputationEventService.listForUser(salah.getId(), office.getId()))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("not an active member");

        assertThatThrownBy(() -> reputationEventService.listForUser(salah.getId(), hostel.getId()))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("not an active member");
    }
}