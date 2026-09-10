package com.borrowbox.repository;

import com.borrowbox.entity.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    Optional<Transaction> findByReservedUnitId(Long reservedUnitId);
}