package com.borrowbox.repository;

import com.borrowbox.entity.Flag;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * V2.4.2 combined flag filter. Any filter may be null to leave it
     * unconstrained; newest incidents first.
     */
    @Query("select f from Flag f where f.community.id = :communityId "
            + "and (:status is null or f.status = :status) "
            + "and (:flagType is null or f.flagType = :flagType) "
            + "and (:transactionId is null or (f.transaction is not null and f.transaction.id = :transactionId)) "
            + "order by f.occurredAt desc, f.id desc")
    List<Flag> findFiltered(@Param("communityId") Long communityId,
                            @Param("status") FlagStatus status,
                            @Param("flagType") FlagType flagType,
                            @Param("transactionId") Long transactionId);

    long countByCommunityIdAndStatus(Long communityId, FlagStatus status);

    /**
     * V2.4.2 recent flags for a community, newest incident first, newest id
     * breaking timestamp ties.
     */
    List<Flag> findTop10ByCommunityIdOrderByOccurredAtDescIdDesc(Long communityId);
}