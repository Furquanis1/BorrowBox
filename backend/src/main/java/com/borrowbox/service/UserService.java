package com.borrowbox.service;

import com.borrowbox.dto.UserCreateRequest;
import com.borrowbox.dto.UserUpdateRequest;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserRole;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.exception.ForeignKeyDeleteConflictTranslator;
import com.borrowbox.exception.ResourceConflictException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    static final String LAST_ACTIVE_ADMIN_MESSAGE = "Cannot delete the last active administrator.";
    static final String ADMIN_LOCK_UNAVAILABLE_MESSAGE =
            "Could not acquire the administrator lock; no changes were made.";
    private static final String ADMIN_LOCK_RELEASE_FAILED_MESSAGE =
            "Failed to release the administrator lock.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ForeignKeyDeleteConflictTranslator deleteConflictTranslator;
    private final TransactionTemplate transactionTemplate;
    private final DataSource dataSource;
    private final int adminLockTimeoutSeconds;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ForeignKeyDeleteConflictTranslator deleteConflictTranslator,
                       PlatformTransactionManager transactionManager,
                       DataSource dataSource,
                       @Value("${borrowbox.user.admin-lock-timeout-seconds:10}") int adminLockTimeoutSeconds) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.deleteConflictTranslator = deleteConflictTranslator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dataSource = dataSource;
        this.adminLockTimeoutSeconds = adminLockTimeoutSeconds;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    public User createUser(UserCreateRequest request) {
        User user = new User(request.fullName(), request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    public User createUser(String fullName, String email, String password) {
        return createUser(new UserCreateRequest(fullName, email, password));
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    public User updateUser(Long id, UserUpdateRequest request) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        existingUser.setFullName(request.fullName());
        existingUser.setEmail(request.email());

        return userRepository.save(existingUser);
    }

    public void deleteUser(Long id) {
        DeletionOutcome outcome = new DeletionOutcome();
        try {
            transactionTemplate.executeWithoutResult(status -> deleteUserInTransaction(id, outcome));
        } catch (RuntimeException ex) {
            if (outcome.releaseFailure != null) {
                ex.addSuppressed(outcome.releaseFailure);
            }
            throw ex;
        }

        if (outcome.releaseFailure == null) {
            return;
        }
        if (outcome.completionStatus == TransactionSynchronization.STATUS_COMMITTED) {
            if (outcome.connectionEvicted) {
                log.error("User deletion committed for user {}, but the administrator lock could not be released. "
                        + "The transaction connection was evicted to prevent unsafe pool reuse.",
                        id, outcome.releaseFailure);
            } else {
                log.error("User deletion committed for user {}, but the administrator lock could not be released "
                        + "and the transaction connection could not be evicted.",
                        id, outcome.releaseFailure);
            }
            return;
        }
        throw outcome.releaseFailure;
    }

    private void deleteUserInTransaction(Long id, DeletionOutcome outcome) {
        AtomicBoolean lockAcquired = new AtomicBoolean(false);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                outcome.completionStatus = status;
                if (lockAcquired.get()) {
                    releaseManageUserLock(outcome);
                }
            }
        });

        Long lockResult = userRepository.acquireManageUserLock(adminLockTimeoutSeconds);
        if (lockResult == null || lockResult != 1L) {
            throw new ResourceConflictException(ADMIN_LOCK_UNAVAILABLE_MESSAGE);
        }
        lockAcquired.set(true);
        outcome.transactionConnection = currentTransactionConnection();

        User existingUser = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (existingUser.getRole() == UserRole.ADMIN && existingUser.getStatus() == UserStatus.ACTIVE) {
            List<User> activeAdmins = userRepository.findByRoleAndStatusForUpdate(
                    UserRole.ADMIN, UserStatus.ACTIVE);
            if (activeAdmins.size() <= 1) {
                throw new ResourceConflictException(LAST_ACTIVE_ADMIN_MESSAGE);
            }
        }

        try {
            userRepository.delete(existingUser);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw deleteConflictTranslator.translate(ex);
        }
    }

    private Connection currentTransactionConnection() {
        Object resource = TransactionSynchronizationManager.getResource(dataSource);
        if (resource instanceof ConnectionHolder holder) {
            return holder.getConnection();
        }
        return null;
    }

    private void releaseManageUserLock(DeletionOutcome outcome) {
        try {
            releaseManageUserLockOrThrow();
        } catch (RuntimeException ex) {
            outcome.releaseFailure = ex;
            outcome.connectionEvicted = evictTransactionConnection(outcome);
        }
    }

    private boolean evictTransactionConnection(DeletionOutcome outcome) {
        try {
            Connection connection = outcome.transactionConnection;
            if (connection == null) {
                throw new IllegalStateException("The transaction-bound connection is not available for eviction.");
            }
            if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
                throw new IllegalStateException("The configured DataSource does not support connection eviction.");
            }
            hikariDataSource.evictConnection(connection);
            return true;
        } catch (RuntimeException ex) {
            outcome.releaseFailure.addSuppressed(ex);
            log.error("Failed to evict the transaction connection after an advisory-lock release failure.", ex);
            return false;
        }
    }

    private void releaseManageUserLockOrThrow() {
        Long lockReleased;
        try {
            lockReleased = userRepository.releaseManageUserLock();
        } catch (RuntimeException ex) {
            throw new IllegalStateException(ADMIN_LOCK_RELEASE_FAILED_MESSAGE, ex);
        }
        if (lockReleased == null || lockReleased != 1L) {
            throw new IllegalStateException(ADMIN_LOCK_RELEASE_FAILED_MESSAGE);
        }
    }

    private static final class DeletionOutcome {
        private Connection transactionConnection;
        private RuntimeException releaseFailure;
        private int completionStatus = -1;
        private boolean connectionEvicted;
    }
}
