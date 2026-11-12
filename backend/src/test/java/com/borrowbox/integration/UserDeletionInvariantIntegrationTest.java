package com.borrowbox.integration;

import com.borrowbox.dto.LoginRequest;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserRole;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.exception.ResourceConflictException;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@Timeout(60)
@TestPropertySource(properties = "borrowbox.user.admin-lock-timeout-seconds=3")
public class UserDeletionInvariantIntegrationTest {

    private static final String PASSWORD = "Gate11Deletion!123";
    private static final String LAST_ACTIVE_ADMIN_MESSAGE =
            "Cannot delete the last active administrator.";
    private static final String LOCK_UNAVAILABLE_MESSAGE =
            "Could not acquire the administrator lock; no changes were made.";
    private static final String CONFLICT_MESSAGE =
            "Cannot delete because related records still reference this resource.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private DataSource dataSource;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdAssetIds = new ArrayList<>();
    private List<Long> baselineActiveAdminIds = new ArrayList<>();

    @BeforeEach
    void suspendBaselineActiveAdmins() {
        createdUserIds.clear();
        createdAssetIds.clear();
        baselineActiveAdminIds = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .toList();

        if (!baselineActiveAdminIds.isEmpty()) {
            List<User> admins = userRepository.findAllById(baselineActiveAdminIds);
            admins.forEach(admin -> admin.setStatus(UserStatus.SUSPENDED));
            userRepository.saveAllAndFlush(admins);
        }
    }

    @AfterEach
    void cleanUpCreatedRowsAndRestoreBaselineAdmins() {
        createdAssetIds.forEach(id -> assetRepository.findById(id).ifPresent(assetRepository::delete));
        assetRepository.flush();
        createdUserIds.forEach(id -> userRepository.findById(id).ifPresent(userRepository::delete));
        userRepository.flush();

        if (!baselineActiveAdminIds.isEmpty()) {
            List<User> admins = userRepository.findAllById(baselineActiveAdminIds);
            admins.forEach(admin -> admin.setStatus(UserStatus.ACTIVE));
            userRepository.saveAllAndFlush(admins);
        }
    }

    @Test
    void lastActiveAdminDeleteIsRejectedAndReleasesTheLock() throws Exception {
        User admin = createActiveAdmin("gate11.last.admin");
        Cookie cookie = login(admin.getEmail());

        mockMvc.perform(delete("/api/users/{id}", admin.getId()).cookie(cookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(LAST_ACTIVE_ADMIN_MESSAGE));

        assertThat(userRepository.findById(admin.getId())).isPresent();
        assertThat(advisoryLockConnectionId()).isNull();
    }

    @Test
    void activeAdminCanDeleteAnotherActiveAdmin() throws Exception {
        User actor = createActiveAdmin("gate11.actor.admin");
        User target = createActiveAdmin("gate11.target.admin");
        Cookie cookie = login(actor.getEmail());

        mockMvc.perform(delete("/api/users/{id}", target.getId()).cookie(cookie))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(target.getId())).isEmpty();
        assertThat(userRepository.findById(actor.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(advisoryLockConnectionId()).isNull();
    }

    @Test
    void activeAdminCanDeleteSelfWhenAnotherActiveAdminRemains() throws Exception {
        User actor = createActiveAdmin("gate11.self.admin");
        User other = createActiveAdmin("gate11.other.admin");
        Cookie cookie = login(actor.getEmail());

        mockMvc.perform(delete("/api/users/{id}", actor.getId()).cookie(cookie))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(actor.getId())).isEmpty();
        assertThat(userRepository.findById(other.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(advisoryLockConnectionId()).isNull();
    }

    @Test
    void suspendedAdminCanBeDeletedWhileAnActiveAdminRemains() throws Exception {
        User actor = createActiveAdmin("gate11.active.actor");
        User suspended = createUser("gate11.suspended.admin");
        suspended.setRole(UserRole.ADMIN);
        suspended.setStatus(UserStatus.SUSPENDED);
        suspended = userRepository.saveAndFlush(suspended);
        Cookie cookie = login(actor.getEmail());

        mockMvc.perform(delete("/api/users/{id}", suspended.getId()).cookie(cookie))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(suspended.getId())).isEmpty();
        assertThat(userRepository.findById(actor.getId())).isPresent();
        assertThat(advisoryLockConnectionId()).isNull();
    }

    @Test
    void missingUserDeleteReturnsNotFoundAndReleasesTheLock() throws Exception {
        User admin = createActiveAdmin("gate11.missing.admin");
        Cookie cookie = login(admin.getEmail());

        mockMvc.perform(delete("/api/users/{id}", Long.MAX_VALUE).cookie(cookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());

        assertThat(userRepository.findById(admin.getId())).isPresent();
        assertThat(advisoryLockConnectionId()).isNull();
    }

    @Test
    void operatorLockContentionTimesOutWithoutDeleting() throws Exception {
        User target = createUser("gate11.lock.target");

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(true);
            assertThat(acquireOperatorLock(lockConnection)).isEqualTo(1L);
            try {
                assertThatThrownBy(() -> userService.deleteUser(target.getId()))
                        .isInstanceOf(ResourceConflictException.class)
                        .hasMessage(LOCK_UNAVAILABLE_MESSAGE);
            } finally {
                assertThat(releaseOperatorLock(lockConnection)).isEqualTo(1L);
            }
        }

        assertThat(userRepository.findById(target.getId())).isPresent();
        assertThat(advisoryLockConnectionId()).isNull();

        userService.deleteUser(target.getId());

        assertThat(userRepository.findById(target.getId())).isEmpty();
    }

    @Test
    void foreignKeyConflictPreservesRowsAndReleasesTheLock() throws Exception {
        User target = createUser("gate11.fk.owner");
        Asset asset = new Asset();
        asset.setOwner(target);
        asset.setTitle("Gate11 FK Asset " + UUID.randomUUID());
        asset.setStatus(AssetStatus.ACTIVE);
        asset = assetRepository.saveAndFlush(asset);
        createdAssetIds.add(asset.getId());

        assertThatThrownBy(() -> userService.deleteUser(target.getId()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(CONFLICT_MESSAGE);

        assertThat(userRepository.findById(target.getId())).isPresent();
        assertThat(assetRepository.findById(asset.getId())).isPresent();
        assertThat(advisoryLockConnectionId()).isNull();
    }

    @Test
    void concurrentDeletesOfTwoActiveAdminsLeaveExactlyOne() throws Exception {
        User first = createActiveAdmin("gate11.race.first");
        User second = createActiveAdmin("gate11.race.second");
        CyclicBarrier startGate = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> deleteSecond = pool.submit(() -> {
                startGate.await(5, TimeUnit.SECONDS);
                userService.deleteUser(second.getId());
                return null;
            });
            Future<?> deleteFirst = pool.submit(() -> {
                startGate.await(5, TimeUnit.SECONDS);
                userService.deleteUser(first.getId());
                return null;
            });

            int successes = 0;
            int conflicts = 0;
            for (Future<?> future : List.of(deleteSecond, deleteFirst)) {
                try {
                    future.get(20, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause())
                            .isInstanceOf(ResourceConflictException.class)
                            .hasMessage(LAST_ACTIVE_ADMIN_MESSAGE);
                    conflicts++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }

        long remainingActiveAdmins = Stream.of(first.getId(), second.getId())
                .map(userRepository::findById)
                .flatMap(Optional::stream)
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .count();
        assertThat(remainingActiveAdmins).isEqualTo(1);
        assertThat(advisoryLockConnectionId()).isNull();
    }

    @Test
    void advisoryLockIsHeldUntilTheTransactionCompletesAndReleasedAfterward() throws Exception {
        User target = createActiveAdmin("gate11.held.target");
        User remaining = createActiveAdmin("gate11.held.remaining");

        try (Connection rowBlocker = dataSource.getConnection()) {
            rowBlocker.setAutoCommit(false);
            lockUserRow(rowBlocker, target.getId());
            ExecutorService pool = Executors.newSingleThreadExecutor();

            try {
                Future<?> deletion = pool.submit(() -> {
                    userService.deleteUser(target.getId());
                    return null;
                });

                awaitAdvisoryLockHeld();
                rowBlocker.rollback();
                deletion.get(20, TimeUnit.SECONDS);
                awaitAdvisoryLockFree();
            } finally {
                if (!rowBlocker.getAutoCommit()) {
                    rowBlocker.rollback();
                }
                pool.shutdownNow();
                pool.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        assertThat(userRepository.findById(target.getId())).isEmpty();
        assertThat(userRepository.findById(remaining.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(advisoryLockConnectionId()).isNull();
    }

    private User createActiveAdmin(String prefix) {
        User user = createUser(prefix);
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private User createUser(String prefix) {
        User user = userService.createUser(
                "Gate11 User",
                prefix + "." + UUID.randomUUID() + "@example.com",
                PASSWORD);
        createdUserIds.add(user.getId());
        return user;
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).as("login must set the jwt cookie").isNotNull();
        assertThat(setCookie).startsWith("jwt=");
        int separator = setCookie.indexOf(';');
        assertThat(separator).as("jwt cookie must have attributes").isGreaterThan("jwt=".length());
        return new Cookie("jwt", setCookie.substring("jwt=".length(), separator));
    }

    private void lockUserRow(Connection connection, Long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM users WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("target row must exist before locking").isTrue();
            }
        }
    }

    private Long acquireOperatorLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT GET_LOCK(CONCAT('bb_manage_user_', MD5(DATABASE())), 1)");
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            long value = resultSet.getLong(1);
            return resultSet.wasNull() ? null : value;
        }
    }

    private Long releaseOperatorLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT RELEASE_LOCK(CONCAT('bb_manage_user_', MD5(DATABASE())))");
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            long value = resultSet.getLong(1);
            return resultSet.wasNull() ? null : value;
        }
    }

    private Long advisoryLockConnectionId() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT IS_USED_LOCK(CONCAT('bb_manage_user_', MD5(DATABASE())))");
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            long value = resultSet.getLong(1);
            return resultSet.wasNull() ? null : value;
        }
    }

    private void awaitAdvisoryLockHeld() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (advisoryLockConnectionId() != null) {
                return;
            }
            Thread.sleep(25);
        }
        fail("The deletion transaction never acquired the administrator advisory lock.");
    }

    private void awaitAdvisoryLockFree() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (advisoryLockConnectionId() == null) {
                return;
            }
            Thread.sleep(25);
        }
        fail("The administrator advisory lock was not released after transaction completion.");
    }
}
