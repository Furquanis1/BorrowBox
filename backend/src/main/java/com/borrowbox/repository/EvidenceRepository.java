package com.borrowbox.repository;

import com.borrowbox.entity.Evidence;
import com.borrowbox.entity.EvidenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    /**
     * V2.5.3: capturedAt is the intended chronological sort key, but MySQL
     * DATETIME(6) ties are real when several photos are uploaded in the same
     * microsecond or the clock is coarse, and without a tiebreaker the row
     * order within a tie is not guaranteed. Appending id makes the ordering
     * total and therefore reproducible for every reader of this method.
     */
    List<Evidence> findByTransactionIdOrderByCapturedAtAscIdAsc(Long transactionId);

    List<Evidence> findByTransactionIdAndType(Long transactionId, EvidenceType type);

    Optional<Evidence> findByIdAndTransactionId(Long id, Long transactionId);
}