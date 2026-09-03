package com.borrowbox.repository;

import com.borrowbox.entity.Community;
import com.borrowbox.entity.Membership;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

    List<Community> findByCreatedById(Long createdById);

    Optional<Community> findByCreatedByIdAndActiveNameKey(Long createdById, String activeNameKey);

    boolean existsByCreatedByIdAndActiveNameKey(Long createdById, String activeNameKey);

    @Query("SELECT DISTINCT c FROM Community c JOIN c.memberships m WHERE m.user.id = :userId")
    List<Community> findCommunitiesForUser(@Param("userId") Long userId);

    @Query("SELECT m FROM Membership m WHERE m.community.id = :communityId")
    List<Membership> findMembershipsByCommunityId(@Param("communityId") Long communityId);

    /**
     * Locks a single community row with PESSIMISTIC_WRITE, serializing
     * concurrent rule mutations so the single-active-per-type invariant is
     * evaluated against locked state only (never an unlocked check-then-act).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Community c where c.id = :id")
    Optional<Community> findByIdForUpdate(@Param("id") Long id);
}
