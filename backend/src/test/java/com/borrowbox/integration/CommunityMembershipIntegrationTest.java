package com.borrowbox.integration;

import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityAdmissionMode;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.MembershipVerificationMethod;
import com.borrowbox.entity.User;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
public class CommunityMembershipIntegrationTest {

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    private User user(String prefix) {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        User u = userRepository.findByEmail(email).orElseGet(() -> {
            User nu = new User(prefix, email);
            nu.setPasswordHash("test-password");
            nu.setStatus(com.borrowbox.entity.UserStatus.ACTIVE);
            return userRepository.save(nu);
        });
        return u;
    }

    @Test
    void createCommunityPersistsAndMakesCreatorActiveManager() {
        User creator = user("Alpha");
        CommunityCreateRequest req = new CommunityCreateRequest(
                "Test " + UUID.randomUUID(), "desc", CommunityType.COLLEGE, null, null, null, null);

        var response = communityService.createCommunity(req, creator);

        Community loaded = communityRepository.findById(response.id()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(loaded.getAdmissionMode()).isEqualTo(CommunityAdmissionMode.MANAGER_APPROVAL);

        List<Membership> memberships = membershipRepository.findByCommunityId(loaded.getId());
        assertThat(memberships).hasSize(1);
        Membership m = memberships.get(0);
        assertThat(m.getUser().getId()).isEqualTo(creator.getId());
        assertThat(m.getRole()).isEqualTo(MembershipRole.MANAGER);
        assertThat(m.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(m.getVerificationMethod()).isEqualTo(MembershipVerificationMethod.ADMIN);
    }

    @Test
    void membershipUniquenessEnforcedByDatabase() {
        User creator = user("Beta");
        CommunityCreateRequest req = new CommunityCreateRequest(
                "Uniq " + UUID.randomUUID(), null, CommunityType.HOSTEL, null, null, null, null);
        var response = communityService.createCommunity(req, creator);
        Community community = communityRepository.findById(response.id()).orElseThrow();

        User second = user("Beta2");
        Membership m1 = new Membership();
        m1.setUser(second);
        m1.setCommunity(community);
        m1.setRole(MembershipRole.MEMBER);
        m1.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.saveAndFlush(m1);

        Membership m2 = new Membership();
        m2.setUser(second);
        m2.setCommunity(community);
        m2.setRole(MembershipRole.MEMBER);
        m2.setStatus(MembershipStatus.ACTIVE);

        assertThatThrownBy(() -> membershipRepository.saveAndFlush(m2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeNameUniquenessEnforcedByDatabaseForSameCreator() {
        User creator = user("Gamma");
        Community c1 = new Community();
        c1.setName("SameName");
        c1.setType(CommunityType.CLUB);
        c1.setStatus(CommunityStatus.ACTIVE);
        c1.setAdmissionMode(CommunityAdmissionMode.MANAGER_APPROVAL);
        c1.setCreatedBy(creator);
        c1.setActiveNameKey("samename");
        communityRepository.saveAndFlush(c1);

        Community c2 = new Community();
        c2.setName("SameName");
        c2.setType(CommunityType.CLUB);
        c2.setStatus(CommunityStatus.ACTIVE);
        c2.setAdmissionMode(CommunityAdmissionMode.MANAGER_APPROVAL);
        c2.setCreatedBy(creator);
        c2.setActiveNameKey("samename");

        assertThatThrownBy(() -> communityRepository.saveAndFlush(c2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void archivedDuplicatesAllowedForSameCreator() {
        User creator = user("Delta");
        Community active = new Community();
        active.setName("Archivable");
        active.setType(CommunityType.OTHER);
        active.setStatus(CommunityStatus.ACTIVE);
        active.setAdmissionMode(CommunityAdmissionMode.MANAGER_APPROVAL);
        active.setCreatedBy(creator);
        active.setActiveNameKey("archivable");
        communityRepository.saveAndFlush(active);

        Community archived = new Community();
        archived.setName("Archivable");
        archived.setType(CommunityType.OTHER);
        archived.setStatus(CommunityStatus.ARCHIVED);
        archived.setAdmissionMode(CommunityAdmissionMode.MANAGER_APPROVAL);
        archived.setCreatedBy(creator);
        archived.setActiveNameKey(null);
        communityRepository.saveAndFlush(archived);

        assertThat(archived.getId()).isNotNull();
    }

    @Test
    void contextMetadataJsonRoundTrips() {
        User creator = user("Epsilon");
        CommunityCreateRequest req = new CommunityCreateRequest(
                "Ctx " + UUID.randomUUID(), null, CommunityType.COLLEGE, null, null, null, null);
        var response = communityService.createCommunity(req, creator);
        Community community = communityRepository.findById(response.id()).orElseThrow();

        Map<String, Object> metadata = Map.of("program", "CSE", "year", 4, "section", "A");
        User member = user("Epsilon2");
        Membership m = new Membership();
        m.setUser(member);
        m.setCommunity(community);
        m.setRole(MembershipRole.MEMBER);
        m.setStatus(MembershipStatus.ACTIVE);
        m.setJoinedAt(LocalDateTime.now());
        m.setContextMetadata(metadata);
        Membership saved = membershipRepository.saveAndFlush(m);

        Membership loaded = membershipRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getContextMetadata()).isNotNull();
        assertThat(loaded.getContextMetadata().get("program")).isEqualTo("CSE");
        assertThat(((Number) loaded.getContextMetadata().get("year")).intValue()).isEqualTo(4);
        assertThat(loaded.getContextMetadata().get("section")).isEqualTo("A");
    }

    @Test
    void membershipsAreReadableThroughService() {
        User creator = user("Zeta");
        CommunityCreateRequest req = new CommunityCreateRequest(
                "Read " + UUID.randomUUID(), null, CommunityType.OFFICE, null, null, null, null);
        var response = communityService.createCommunity(req, creator);

        List<MembershipResponse> members = communityService.listMembers(response.id(), creator);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).role()).isEqualTo(MembershipRole.MANAGER);
        assertThat(members.get(0).userFullName()).isEqualTo(creator.getFullName());
    }
}
