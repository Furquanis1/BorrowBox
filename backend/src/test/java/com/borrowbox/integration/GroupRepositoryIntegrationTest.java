package com.borrowbox.integration;

import com.borrowbox.entity.Group;
import com.borrowbox.entity.User;
import com.borrowbox.repository.GroupRepository;
import com.borrowbox.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Objects;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class GroupRepositoryIntegrationTest {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndLoadGroupWithUsersFromMySql() {
        User user = userRepository.save(testUser("Group Member", "group.member@example.com"));

        Group group = new Group("Weekend Crew", "Friends who share items");
        group.addUser(user);

        Group saved = groupRepository.save(group);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsers()).hasSize(1);

        Long savedId = Objects.requireNonNull(saved.getId());
        Group loaded = groupRepository.findById(savedId).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Weekend Crew");
        assertThat(loaded.getUsers()).hasSize(1);
        assertThat(loaded.getUsers().iterator().next().getEmail()).isEqualTo("group.member@example.com");
    }

    private User testUser(String fullName, String email) {
        User user = new User(fullName, email);
        user.setPasswordHash("test-password");
        return user;
    }
}
