package com.borrowbox.repository;

import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
