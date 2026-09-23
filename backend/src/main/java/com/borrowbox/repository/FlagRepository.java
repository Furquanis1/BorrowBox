package com.borrowbox.repository;

import com.borrowbox.entity.Flag;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlagRepository extends JpaRepository<Flag, Long> {

    List<Flag> findByCommunityId(Long communityId);

    List<Flag> findByCommunityIdAndStatus(Long communityId, FlagStatus status);

    List<Flag> findByCommunityIdAndFlagType(Long communityId, FlagType flagType);

    List<Flag> findByTransactionId(Long transactionId);

    List<Flag> findByCommunityIdAndTransactionId(Long communityId, Long transactionId);

    Optional<Flag> findByIdAndCommunityId(Long id, Long communityId);
}