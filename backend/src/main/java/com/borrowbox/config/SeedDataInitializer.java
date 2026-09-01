package com.borrowbox.config;

import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityAdmissionMode;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.MembershipVerificationMethod;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * V2.1.1 deterministic, idempotent development seed.
 *
 * Seeds only Users + Communities + Memberships. Asset/AssetUnit/CommunityListing
 * seed data arrives with its own implementation slice.
 *
 * Idempotency keys:
 *   users        -> email
 *   communities  -> (created_by, name)
 *   memberships  -> (user_id, community_id)
 */
@Component
public class SeedDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDataInitializer(UserRepository userRepository,
                               CommunityRepository communityRepository,
                               MembershipRepository membershipRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Value("${borrowbox.seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        seed();
    }

    @Transactional
    public void seed() {
        User ahmed = user("Ahmed", "ahmed@example.com", "password123");
        User salah = user("Salah", "salah@example.com", "password123");
        User omar = user("Omar", "omar@example.com", "password123");
        User youssef = user("Youssef", "youssef@example.com", "password123");
        User karim = user("Karim", "karim@example.com", "password123");

        Community cse = community(
                "CSE Department", "Computer Science academic community",
                CommunityType.COLLEGE, CommunityAdmissionMode.MANAGER_APPROVAL, ahmed,
                new BigDecimal("29.979200"), new BigDecimal("31.134200"), 2000);
        Community hostel = community(
                "Hostel Block B", "Residential community for Hostel Block B",
                CommunityType.HOSTEL, CommunityAdmissionMode.MANAGER_APPROVAL, omar,
                new BigDecimal("29.985300"), new BigDecimal("31.140000"), 300);
        Community office = community(
                "Engineering Office", "Workplace community for the Engineering Office",
                CommunityType.OFFICE, CommunityAdmissionMode.LOCATION_VERIFIED, omar,
                new BigDecimal("29.987400"), new BigDecimal("31.196800"), 500);

        membership(ahmed, cse, MembershipRole.MANAGER, mapOf("program", "CSE", "year", 4, "section", "A"));
        membership(salah, cse, MembershipRole.MEMBER, mapOf("program", "CSE", "year", 4, "section", "A"));
        membership(omar, hostel, MembershipRole.MANAGER, mapOf("block", "B", "floor", 3, "room", "B-302"));
        membership(youssef, hostel, MembershipRole.MEMBER, mapOf("block", "B", "floor", 3, "room", "B-304"));
        membership(ahmed, office, MembershipRole.MEMBER, mapOf("department", "Engineering", "team", "Platform"));
        membership(karim, office, MembershipRole.MEMBER, mapOf("department", "Engineering", "team", "QA"));
        membership(omar, office, MembershipRole.MANAGER, mapOf("department", "Engineering", "team", "Leadership"));
    }

    private User user(String fullName, String email, String rawPassword) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User(fullName, email);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setStatus(UserStatus.ACTIVE);
            return userRepository.save(user);
        });
    }

    private Community community(String name, String description, CommunityType type,
                                CommunityAdmissionMode admissionMode, User createdBy,
                                BigDecimal latitude, BigDecimal longitude, Integer radiusM) {
        String activeNameKey = name.trim().toLowerCase(java.util.Locale.ROOT);
        return communityRepository
                .findByCreatedByIdAndActiveNameKey(createdBy.getId(), activeNameKey)
                .orElseGet(() -> {
                    Community community = new Community();
                    community.setName(name);
                    community.setDescription(description);
                    community.setType(type);
                    community.setStatus(CommunityStatus.ACTIVE);
                    community.setAdmissionMode(admissionMode);
                    community.setCreatedBy(createdBy);
                    community.setLocationLatitude(latitude);
                    community.setLocationLongitude(longitude);
                    community.setLocationRadiusM(radiusM);
                    community.setActiveNameKey(activeNameKey);
                    return communityRepository.save(community);
                });
    }

    private void membership(User user, Community community, MembershipRole role,
                            Map<String, Object> contextMetadata) {
        if (membershipRepository.existsByUserIdAndCommunityId(user.getId(), community.getId())) {
            return;
        }
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setCommunity(community);
        membership.setRole(role);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setVerificationMethod(MembershipVerificationMethod.ADMIN);
        membership.setVerifiedBy(user);
        membership.setVerifiedAt(LocalDateTime.now());
        membership.setJoinedAt(LocalDateTime.now());
        membership.setContextMetadata(contextMetadata);
        membershipRepository.save(membership);
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return map;
    }
}
