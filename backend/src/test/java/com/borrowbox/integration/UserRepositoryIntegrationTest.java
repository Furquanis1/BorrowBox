package com.borrowbox.integration;

import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndLoadUserFromMySql() {
        User user = testUser("Test User", "test.user@example.com");
        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();

        User loaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getFullName()).isEqualTo("Test User");
        assertThat(loaded.getEmail()).isEqualTo("test.user@example.com");
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    private User testUser(String fullName, String email) {
        User user = new User(fullName, email);
        user.setPasswordHash("test-password");
        return user;
    }
}
