package com.borrowbox.repository;

import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    List<Membership> findByUserId(Long userId);

    List<Membership> findByCommunityId(Long communityId);

    Optional<Membership> findByUserIdAndCommunityId(Long userId, Long communityId);

    boolean existsByUserIdAndCommunityId(Long userId, Long communityId);

    Optional<Membership> findByUserIdAndCommunityIdAndStatus(
            Long userId, Long communityId, MembershipStatus status);

    /**
     * Locks a single membership row with PESSIMISTIC_WRITE, serializing
     * concurrent admission decisions on the same membership.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membership m where m.id = :id")
    Optional<Membership> findByIdForUpdate(@Param("id") Long id);

    /**
     * Locks every ACTIVE MANAGER membership row for a community with
     * PESSIMISTIC_WRITE, so concurrent manager-leave operations are
     * serialized before the "last active manager" invariant is evaluated
     * in memory against the actually-locked rows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membership m where m.community.id = :communityId"
            + " and m.status = com.borrowbox.entity.MembershipStatus.ACTIVE"
            + " and m.role = com.borrowbox.entity.MembershipRole.MANAGER")
    List<Membership> findActiveManagersForUpdate(@Param("communityId") Long communityId);
}
