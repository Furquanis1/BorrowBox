package com.borrowbox.repository;

import com.borrowbox.entity.TransactionMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionMessageRepository extends JpaRepository<TransactionMessage, Long> {

    /**
     * Full conversation for a transaction in the order participants read it:
     * chronicle-ascending (server-provided createdAt).
     */
    List<TransactionMessage> findByTransactionIdOrderByCreatedAtAsc(Long transactionId);
}