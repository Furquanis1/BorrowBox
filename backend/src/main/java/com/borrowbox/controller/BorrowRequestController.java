package com.borrowbox.controller;

import com.borrowbox.dto.BorrowRequestCreateRequest;
import com.borrowbox.entity.BorrowRequest;
import com.borrowbox.service.BorrowRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/borrow-requests")
public class BorrowRequestController {

    private final BorrowRequestService borrowRequestService;

    public BorrowRequestController(BorrowRequestService borrowRequestService) {
        this.borrowRequestService = borrowRequestService;
    }

    @GetMapping
    public ResponseEntity<List<BorrowRequest>> getAllBorrowRequests() {
        return ResponseEntity.ok(borrowRequestService.getAllBorrowRequests());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BorrowRequest> getBorrowRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(borrowRequestService.getBorrowRequestById(id));
    }

    @PostMapping
    public ResponseEntity<BorrowRequest> createBorrowRequest(@Valid @RequestBody BorrowRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(borrowRequestService.createBorrowRequest(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BorrowRequest> updateBorrowRequest(@PathVariable Long id, @Valid @RequestBody BorrowRequestCreateRequest request) {
        return ResponseEntity.ok(borrowRequestService.updateBorrowRequest(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBorrowRequest(@PathVariable Long id) {
        borrowRequestService.deleteBorrowRequest(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<BorrowRequest> approveBorrowRequest(@PathVariable Long id) {
        return ResponseEntity.ok(borrowRequestService.approveBorrowRequest(id));
    }
}