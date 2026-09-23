package com.borrowbox.repository;

import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Locks a single transaction row with PESSIMISTIC_WRITE, serializing
     * concurrent lifecycle decisions on the same transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") Long id);

    List<Transaction> findByBorrowerIdOrderByIdDesc(Long borrowerId);

    List<Transaction> findByLenderIdOrderByIdDesc(Long lenderId);

    List<Transaction> findByBorrowerIdAndCommunityIdOrderByIdDesc(Long borrowerId, Long communityId);

    List<Transaction> findByLenderIdAndCommunityIdOrderByIdDesc(Long lenderId, Long communityId);

    Optional<Transaction> findByReservedUnitId(Long reservedUnitId);

    Optional<Transaction> findByAssetIdAndBorrowerIdAndStateIn(
            Long assetId, Long borrowerId, List<TransactionStatus> states);

    List<Transaction> findByAssetIdOrderByIdDesc(Long assetId);

    Optional<Transaction> findByIdAndCommunityId(Long id, Long communityId);

    List<Transaction> findByCommunityIdAndState(Long communityId, TransactionStatus state);

    long countByCommunityIdAndStateIn(Long communityId, List<TransactionStatus> states);

    long countByCommunityIdAndState(Long communityId, TransactionStatus state);

    long countByCommunityIdAndStateAndDueAtIsNotNull(Long communityId, TransactionStatus state);

    /**
     * V2.4.2 completed loans returned on or before their due date, compared in
     * SQL so both columns use the same persisted server-clock values.
     */
    @Query("select count(t) from Transaction t "
            + "where t.community.id = :communityId and t.state = :state "
            + "and t.dueAt is not null and t.completedAt is not null and t.completedAt <= t.dueAt")
    long countCompletedOnTime(@Param("communityId") Long communityId,
                              @Param("state") TransactionStatus state);

    long countByCommunityIdAndCreatedAtGreaterThanEqual(Long communityId, LocalDateTime from);
}