package com.borrowbox.service;

import com.borrowbox.dto.UserCreateRequest;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserRole;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.exception.ForeignKeyDeleteConflictTranslator;
import com.borrowbox.exception.ResourceConflictException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private Connection transactionConnection;

    private RecordingTransactionManager transactionManager;
    private UserService userService;

    @BeforeEach
    void setUp() {
        transactionManager = new RecordingTransactionManager();
        userService = new UserService(
                userRepository,
                passwordEncoder,
                new ForeignKeyDeleteConflictTranslator(),
                transactionManager,
                dataSource,
                10);
    }

    @AfterEach
    void cleanupTransactionResources() {
        if (TransactionSynchronizationManager.hasResource(dataSource)) {
            TransactionSynchronizationManager.unbindResource(dataSource);
        }
    }

    @Test
    void createUserDefaultsToActiveStatus() {
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser(new UserCreateRequest("Alice", "alice@example.com", "secret123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deleteUserRejectsLastActiveAdminAndReleasesLockAfterRollback() {
        User admin = user(7L, UserRole.ADMIN, UserStatus.ACTIVE);
        stubLockWithReleaseEvent(1L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(admin));
        when(userRepository.findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin));

        assertThatThrownBy(() -> userService.deleteUser(7L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(UserService.LAST_ACTIVE_ADMIN_MESSAGE);

        verify(userRepository, never()).delete(any(User.class));
        verify(userRepository, never()).flush();
        assertThat(transactionManager.events()).containsExactly("begin", "rollback", "release");
    }

    @Test
    void deleteUserAllowsActiveAdminWhenAnotherActiveAdminRemains() {
        User admin = user(1L, UserRole.ADMIN, UserStatus.ACTIVE);
        User other = user(2L, UserRole.ADMIN, UserStatus.ACTIVE);
        stubLockWithReleaseEvent(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin, other));

        userService.deleteUser(1L);

        InOrder order = inOrder(userRepository);
        order.verify(userRepository).acquireManageUserLock(10);
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(userRepository).findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE);
        order.verify(userRepository).delete(admin);
        order.verify(userRepository).flush();
        order.verify(userRepository).releaseManageUserLock();
        verify(dataSource, never()).evictConnection(any(Connection.class));
        assertThat(transactionManager.events()).containsExactly("begin", "commit", "release");
    }

    @Test
    void deleteUserAllowsSuspendedAdminWithoutActiveAdminLock() {
        User suspendedAdmin = user(3L, UserRole.ADMIN, UserStatus.SUSPENDED);
        stubLockWithReleaseEvent(1L);
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(suspendedAdmin));

        userService.deleteUser(3L);

        verify(userRepository, never())
                .findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE);
        verify(userRepository).delete(suspendedAdmin);
        verify(userRepository).flush();
        assertThat(transactionManager.events()).containsExactly("begin", "commit", "release");
    }

    @Test
    void deleteUserMissingTargetReturnsNotFoundAndReleasesLock() {
        stubLockWithReleaseEvent(1L);
        when(userRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found with id: 99");

        verify(userRepository, never()).delete(any(User.class));
        assertThat(transactionManager.events()).containsExactly("begin", "rollback", "release");
    }

    @Test
    void deleteUserLockTimeoutReturnsConflictWithoutReadingOrDeletingTarget() {
        when(userRepository.acquireManageUserLock(10)).thenReturn(0L);

        assertThatThrownBy(() -> userService.deleteUser(4L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(UserService.ADMIN_LOCK_UNAVAILABLE_MESSAGE);

        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userRepository, never()).delete(any(User.class));
        verify(userRepository, never()).releaseManageUserLock();
        verify(dataSource, never()).evictConnection(any(Connection.class));
        assertThat(transactionManager.events()).containsExactly("begin", "rollback");
    }

    @Test
    void deleteUserTranslatesForeignKeyConflictAndReleasesLockAfterRollback() {
        User target = user(5L, UserRole.USER, UserStatus.ACTIVE);
        stubLockWithReleaseEvent(1L);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(target));
        DataIntegrityViolationException conflict = new DataIntegrityViolationException(
                "delete failed",
                new SQLException("parent row referenced", "23000", 1451));
        doThrow(conflict).when(userRepository).flush();

        assertThatThrownBy(() -> userService.deleteUser(5L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Cannot delete because related records still reference this resource.");

        verify(userRepository).delete(target);
        assertThat(transactionManager.events()).containsExactly("begin", "rollback", "release");
    }

    @Test
    void deleteUserTreatsReleaseFailureAfterSuccessfulCommitAsSuccessAndEvictsConnection() {
        User admin = user(6L, UserRole.ADMIN, UserStatus.ACTIVE);
        User other = user(8L, UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.acquireManageUserLock(10)).thenReturn(1L);
        when(userRepository.releaseManageUserLock()).thenAnswer(inv -> {
            transactionManager.record("release");
            TransactionSynchronizationManager.unbindResource(dataSource);
            throw new IllegalStateException("release failed");
        });
        when(userRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(admin));
        when(userRepository.findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin, other));
        bindTransactionConnection();

        userService.deleteUser(6L);

        verify(userRepository).delete(admin);
        verify(dataSource).evictConnection(transactionConnection);
        assertThat(transactionManager.events()).containsExactly("begin", "commit", "release");
    }

    @Test
    void deleteUserTreatsZeroReleaseAfterSuccessfulCommitAsSuccessAndEvictsConnection() {
        User admin = user(10L, UserRole.ADMIN, UserStatus.ACTIVE);
        User other = user(11L, UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.acquireManageUserLock(10)).thenReturn(1L);
        when(userRepository.releaseManageUserLock()).thenAnswer(inv -> {
            transactionManager.record("release");
            return 0L;
        });
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(admin));
        when(userRepository.findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin, other));
        bindTransactionConnection();

        userService.deleteUser(10L);

        verify(userRepository).delete(admin);
        verify(dataSource).evictConnection(transactionConnection);
        assertThat(transactionManager.events()).containsExactly("begin", "commit", "release");
    }

    @Test
    void deleteUserPreservesCommitFailureAndAddsReleaseFailure() {
        User admin = user(12L, UserRole.ADMIN, UserStatus.ACTIVE);
        User other = user(13L, UserRole.ADMIN, UserStatus.ACTIVE);
        RuntimeException commitFailure = new IllegalStateException("commit failed");
        transactionManager.failCommitWith(commitFailure);
        when(userRepository.acquireManageUserLock(10)).thenReturn(1L);
        when(userRepository.releaseManageUserLock()).thenAnswer(inv -> {
            transactionManager.record("release");
            throw new IllegalStateException("release failed");
        });
        when(userRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(admin));
        when(userRepository.findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin, other));
        bindTransactionConnection();

        assertThatThrownBy(() -> userService.deleteUser(12L))
                .isSameAs(commitFailure)
                .satisfies(ex -> {
                    assertThat(ex.getSuppressed()).hasSize(1);
                    assertThat(ex.getSuppressed()[0])
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Failed to release the administrator lock.");
                });

        verify(dataSource).evictConnection(transactionConnection);
        assertThat(transactionManager.events()).containsExactly("begin", "commit", "rollback", "release");
    }

    @Test
    void deleteUserAddsReleaseFailureToPrimaryRollbackFailureAndEvictsConnection() {
        User admin = user(9L, UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.acquireManageUserLock(10)).thenReturn(1L);
        when(userRepository.releaseManageUserLock()).thenAnswer(inv -> {
            transactionManager.record("release");
            throw new IllegalStateException("release failed");
        });
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(admin));
        when(userRepository.findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin));
        bindTransactionConnection();

        assertThatThrownBy(() -> userService.deleteUser(9L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(UserService.LAST_ACTIVE_ADMIN_MESSAGE)
                .satisfies(ex -> {
                    assertThat(ex.getSuppressed()).hasSize(1);
                    assertThat(ex.getSuppressed()[0])
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Failed to release the administrator lock.");
                });

        verify(dataSource).evictConnection(transactionConnection);
        assertThat(transactionManager.events()).containsExactly("begin", "rollback", "release");
    }

    private void stubLockWithReleaseEvent(long acquired) {
        when(userRepository.acquireManageUserLock(10)).thenReturn(acquired);
        when(userRepository.releaseManageUserLock()).thenAnswer(inv -> {
            transactionManager.record("release");
            return 1L;
        });
    }

    private void bindTransactionConnection() {
        TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(transactionConnection));
    }

    private User user(Long id, UserRole role, UserStatus status) {
        User user = new User("User " + id, "user" + id + "@example.com");
        user.setId(id);
        user.setPasswordHash("test-password");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {

        private final SimpleTransactionStatus status = new SimpleTransactionStatus();
        private final List<String> events = new ArrayList<>();
        private RuntimeException commitFailure;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.initSynchronization();
            }
            record("begin");
            return status;
        }

        @Override
        public void commit(TransactionStatus status) {
            record("commit");
            if (commitFailure != null) {
                record("rollback");
                complete(TransactionSynchronization.STATUS_ROLLED_BACK);
                throw commitFailure;
            }
            complete(TransactionSynchronization.STATUS_COMMITTED);
        }

        void failCommitWith(RuntimeException commitFailure) {
            this.commitFailure = commitFailure;
        }

        @Override
        public void rollback(TransactionStatus status) {
            record("rollback");
            complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        void record(String event) {
            events.add(event);
        }

        List<String> events() {
            return events;
        }

        private void complete(int status) {
            try {
                for (TransactionSynchronization synchronization
                        : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCompletion(status);
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }
}
