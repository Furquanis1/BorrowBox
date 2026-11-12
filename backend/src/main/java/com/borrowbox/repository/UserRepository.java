package com.borrowbox.repository;

import com.borrowbox.entity.User;
import com.borrowbox.entity.UserRole;
import com.borrowbox.entity.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByEmail(String email);

	@Query(value = "SELECT GET_LOCK(CONCAT('bb_manage_user_', MD5(DATABASE())), :timeoutSeconds)", nativeQuery = true)
	Long acquireManageUserLock(@Param("timeoutSeconds") int timeoutSeconds);

	@Query(value = "SELECT RELEASE_LOCK(CONCAT('bb_manage_user_', MD5(DATABASE())))", nativeQuery = true)
	Long releaseManageUserLock();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.role = :role and u.status = :status")
	List<User> findByRoleAndStatusForUpdate(@Param("role") UserRole role, @Param("status") UserStatus status);
}
