package com.borrowbox.repository;

import com.borrowbox.entity.Evidence;
import com.borrowbox.entity.EvidenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findByTransactionIdOrderByCapturedAtAsc(Long transactionId);

    List<Evidence> findByTransactionIdAndType(Long transactionId, EvidenceType type);

    Optional<Evidence> findByIdAndTransactionId(Long id, Long transactionId);
}