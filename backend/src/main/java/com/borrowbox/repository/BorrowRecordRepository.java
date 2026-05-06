package com.borrowbox.repository;

import com.borrowbox.entity.BorrowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long>, JpaSpecificationExecutor<BorrowRecord> {

    boolean existsByItemIdAndReturnedFalse(Long itemId);

    List<BorrowRecord> findByReturnedFalseAndDueAtBefore(LocalDateTime dateTime);
}