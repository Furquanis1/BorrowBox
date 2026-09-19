package com.borrowbox.repository;

import com.borrowbox.entity.TransactionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionEventRepository extends JpaRepository<TransactionEvent, Long> {

    List<TransactionEvent> findByTransactionIdOrderByCreatedAtAsc(Long transactionId);

    @Query("select e from TransactionEvent e " +
            "join e.transaction t " +
            "where t.borrower.id = :userId or t.lender.id = :userId " +
            "order by e.createdAt desc")
    List<TransactionEvent> findByParticipantOrderByCreatedAtDesc(@Param("userId") Long userId);
}