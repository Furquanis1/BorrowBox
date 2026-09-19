package com.borrowbox.repository;

import com.borrowbox.entity.TransactionEventDelivery;
import com.borrowbox.entity.TransactionEventDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionEventDeliveryRepository extends JpaRepository<TransactionEventDelivery, Long> {

    Optional<TransactionEventDelivery> findByEventIdAndRecipientId(Long eventId, Long recipientId);

    List<TransactionEventDelivery> findByRecipientIdAndStatusOrderByCreatedAtDesc(Long recipientId, TransactionEventDeliveryStatus status);

    @Query("select d from TransactionEventDelivery d " +
            "where d.recipient.id = :userId " +
            "and d.status in :statuses " +
            "order by d.createdAt desc")
    List<TransactionEventDelivery> findByRecipientIdAndStatusInOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("statuses") List<TransactionEventDeliveryStatus> statuses);

    long countByRecipientIdAndStatus(Long recipientId, TransactionEventDeliveryStatus status);
}