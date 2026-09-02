package com.borrowbox.integration;

import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.dto.CommunityJoinRequest;
import com.borrowbox.dto.MembershipResponse;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityAdmissionMode;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.AdmissionDecision;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.MembershipVerificationMethod;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;
import com.borrowbox.service.MembershipService;
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
    private MembershipService membershipService;

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

    // ─── V2.1.2 admission ─────────────────────────────────────────────────

    @Test
    void managerApprovalFullLifecycleJoinPendingApproveActive() {
        User creator = user("MgrA");
        CommunityCreateRequest req = new CommunityCreateRequest(
                "Appr " + UUID.randomUUID(), null, CommunityType.CLUB,
                CommunityAdmissionMode.MANAGER_APPROVAL, null, null, null);
        var community = communityService.createCommunity(req, creator);
        User applicant = user("ApplicantA");

        MembershipResponse joined = membershipService.joinCommunity(
                applicant, community.id(), new CommunityJoinRequest(null, null, Map.of("program", "CSE")));

        assertThat(joined.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(joined.role()).isEqualTo(MembershipRole.MEMBER);
        assertThat(joined.verificationMethod()).isEqualTo(MembershipVerificationMethod.MANAGER_APPROVAL);
        assertThat(joined.verifiedBy()).isNull();
        assertThat(joined.verifiedAt()).isNull();
        assertThat(joined.joinedAt()).isNull();

        MembershipResponse approved = membershipService.decide(joined.id(), AdmissionDecision.APPROVE, creator);

        assertThat(approved.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(approved.verificationMethod()).isEqualTo(MembershipVerificationMethod.MANAGER_APPROVAL);
        assertThat(approved.verifiedBy()).isEqualTo(creator.getId());
        assertThat(approved.verifiedAt()).isNotNull();
        assertThat(approved.joinedAt()).isNotNull();
    }

    @Test
    void duplicatePendingJoinThrowsBusinessRuleError() {
        User creator = user("MgrB");
        CommunityCreateRequest req = new CommunityCreateRequest(
                "Dupe " + UUID.randomUUID(), null, CommunityType.COLLEGE,
                CommunityAdmissionMode.MANAGER_APPROVAL, null, null, null);
        var community = communityService.createCommunity(req, creator);
        User applicant = user("ApplicantB");

        membershipService.joinCommunity(applicant, community.id(), new CommunityJoinRequest(null, null, null));

        assertThatThrownBy(() -> membershipService.joinCommunity(
                applicant, community.id(), new CommunityJoinRequest(null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already pending");
    }

    @Test
    void locationVerifiedInsideRadiusActivatesImmediately() {
        User creator = user("MgrLoc");
        CommunityCreateRequest req = new CommunityCreateRequest(
                "Loc " + UUID.randomUUID(), null, CommunityType.OFFICE,
                CommunityAdmissionMode.LOCATION_VERIFIED,
                new java.math.BigDecimal("29.987400"), new java.math.BigDecimal("31.196800"), 500);
        var community = communityService.createCommunity(req, creator);
        User joinNearby = user("Nearby");

        MembershipResponse joined = membershipService.joinCommunity(
                joinNearby, community.id(),
                new CommunityJoinRequest(new java.math.BigDecimal("29.987500"), new java.math.BigDecimal("31.196900"), null));

        assertThat(joined.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(joined.role()).isEqualTo(MembershipRole.MEMBER);
        assertThat(joined.verificationMethod()).isEqualTo(MembershipVerificationMethod.LOCATION);
        assertThat(joined.verifiedBy()).isNull();
        assertThat(joined.verifiedAt()).isNotNull();
        assertThat(joined.joinedAt()).isNotNull();
    }

    @Test
    void locationVerifiedOutsideRadiusGoesPending() {
        User creator = user("MgrLocOut");
        CommunityCreateRequest req = new CommunityCreateRequest(
                "LocOut " + UUID.randomUUID(), null, CommunityType.OFFICE,
                CommunityAdmissionMode.LOCATION_VERIFIED,
                new java.math.BigDecimal("29.987400"), new java.math.BigDecimal("31.196800"), 100);
        var community = communityService.createCommunity(req, creator);
        User farAway = user("FarAway");

        MembershipResponse joined = membershipService.joinCommunity(
                farAway, community.id(),
                new CommunityJoinRequest(new java.math.BigDecimal("29.990000"), new java.math.BigDecimal("31.200000"), null));

        assertThat(joined.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(joined.role()).isEqualTo(MembershipRole.MEMBER);
        assertThat(joined.verificationMethod()).isEqualTo(MembershipVerificationMethod.MANAGER_APPROVAL);
        assertThat(joined.verifiedBy()).isNull();
        assertThat(joined.verifiedAt()).isNull();
        assertThat(joined.joinedAt()).isNull();
    }

    @Test
    void leavePreservesAuditTrailAndRejoinResetsVerification() {
        User creator = user("MgrLeave");
        CommunityCreateRequest req = new CommunityCreateRequest(
                "Leave " + UUID.randomUUID(), null, CommunityType.CLUB,
                CommunityAdmissionMode.MANAGER_APPROVAL, null, null, null);
        var community = communityService.createCommunity(req, creator);
        User member = user("LeaveMember");

        MembershipResponse joined = membershipService.joinCommunity(
                member, community.id(), new CommunityJoinRequest(null, null, null));
        MembershipResponse approved = membershipService.decide(joined.id(), AdmissionDecision.APPROVE, creator);

        MembershipResponse left = membershipService.leave(member.getId(), community.id());
        assertThat(left.status()).isEqualTo(MembershipStatus.LEFT);
        assertThat(left.verificationMethod()).isEqualTo(MembershipVerificationMethod.MANAGER_APPROVAL);
        assertThat(left.verifiedBy()).isEqualTo(creator.getId());
        assertThat(left.verifiedAt()).isNotNull();
        assertThat(left.joinedAt()).isNotNull();

        MembershipResponse rejoined = membershipService.joinCommunity(
                member, community.id(), new CommunityJoinRequest(null, null, null));
        assertThat(rejoined.id()).isEqualTo(left.id());
        assertThat(rejoined.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(rejoined.role()).isEqualTo(MembershipRole.MEMBER);
        assertThat(rejoined.verificationMethod()).isEqualTo(MembershipVerificationMethod.MANAGER_APPROVAL);
        assertThat(rejoined.verifiedBy()).isNull();
        assertThat(rejoined.verifiedAt()).isNull();
        assertThat(rejoined.joinedAt()).isNull();
    }

    @Test
    void lastActiveManagerCannotLeave() {
        User creator = user("OnlyManager");
        CommunityCreateRequest req = new CommunityCreateRequest(
                "Solo " + UUID.randomUUID(), null, CommunityType.CLUB, null, null, null, null);
        var community = communityService.createCommunity(req, creator);

        assertThatThrownBy(() -> membershipService.leave(creator.getId(), community.id()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("last active manager");
    }
}
