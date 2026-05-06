package com.borrowbox.controller;

import com.borrowbox.dto.BorrowRecordCreateRequest;
import com.borrowbox.entity.BorrowRecord;
import com.borrowbox.service.BorrowRecordService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/borrow-records")
public class BorrowRecordController {

    private final BorrowRecordService borrowRecordService;

    public BorrowRecordController(BorrowRecordService borrowRecordService) {
        this.borrowRecordService = borrowRecordService;
    }

    @GetMapping
    public ResponseEntity<List<BorrowRecord>> getAllBorrowRecords() {
        return ResponseEntity.ok(borrowRecordService.getAllBorrowRecords());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BorrowRecord> getBorrowRecordById(@PathVariable Long id) {
        return ResponseEntity.ok(borrowRecordService.getBorrowRecordById(id));
    }

    @PostMapping
    public ResponseEntity<BorrowRecord> createBorrowRecord(@Valid @RequestBody BorrowRecordCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(borrowRecordService.createBorrowRecord(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BorrowRecord> updateBorrowRecord(@PathVariable Long id, @Valid @RequestBody BorrowRecordCreateRequest request) {
        return ResponseEntity.ok(borrowRecordService.updateBorrowRecord(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBorrowRecord(@PathVariable Long id) {
        borrowRecordService.deleteBorrowRecord(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<BorrowRecord> returnBorrowedItem(@PathVariable Long id) {
        return ResponseEntity.ok(borrowRecordService.returnBorrowedItem(id));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<BorrowRecord>> searchBorrowRecords(
            @RequestParam(required = false, name = "itemId") Long itemId,
            @RequestParam(required = false, name = "borrowedByUserId") Long borrowedByUserId,
            @RequestParam(required = false, name = "active") Boolean showOnlyActive,
            @RequestParam(required = false, name = "overdue") Boolean showOnlyOverdue,
            Pageable pageable
    ) {
        Page<BorrowRecord> page = borrowRecordService.searchBorrowRecords(itemId, borrowedByUserId, showOnlyActive, showOnlyOverdue, pageable);
        return ResponseEntity.ok(page);
    }
}