package com.borrowbox.service;

import com.borrowbox.dto.BorrowRecordCreateRequest;
import com.borrowbox.entity.BorrowRecord;
import com.borrowbox.entity.BorrowRequest;
import com.borrowbox.entity.BorrowRequestStatus;
import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.BorrowRecordRepository;
import com.borrowbox.repository.BorrowRequestRepository;
import com.borrowbox.repository.ItemRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.spec.BorrowRecordSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class BorrowRecordService {

    private final BorrowRecordRepository borrowRecordRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    public BorrowRecordService(BorrowRecordRepository borrowRecordRepository,
                               BorrowRequestRepository borrowRequestRepository,
                               ItemRepository itemRepository,
                               UserRepository userRepository) {
        this.borrowRecordRepository = borrowRecordRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
    }

    public List<BorrowRecord> getAllBorrowRecords() {
        return borrowRecordRepository.findAll();
    }

    public BorrowRecord createBorrowRecord(BorrowRecordCreateRequest request) {
        BorrowRequest borrowRequest = borrowRequestRepository.findById(Objects.requireNonNull(request.borrowRequestId()))
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found with id: " + request.borrowRequestId()));
        Item item = itemRepository.findById(Objects.requireNonNull(request.itemId()))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + request.itemId()));
        User user = userRepository.findById(Objects.requireNonNull(request.borrowedByUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.borrowedByUserId()));

        if (borrowRequest.getStatus() != BorrowRequestStatus.APPROVED) {
            throw new BusinessRuleViolationException("Borrow request must be approved before it can be completed: " + borrowRequest.getId());
        }

        if (borrowRequest.getItem() == null || !Objects.equals(borrowRequest.getItem().getId(), item.getId())) {
            throw new BusinessRuleViolationException("Borrow record item must match the approved request item");
        }

        if (borrowRequest.getRequestedBy() == null || !Objects.equals(borrowRequest.getRequestedBy().getId(), user.getId())) {
            throw new BusinessRuleViolationException("Borrow record user must match the approved request user");
        }

        if (item.isArchived()) {
            throw new BusinessRuleViolationException("Cannot borrow an archived item: " + item.getId());
        }

        if (item.getStatus() != ItemStatus.APPROVED) {
            throw new BusinessRuleViolationException("Item must be approved before borrowing: " + item.getId());
        }

        if (Objects.requireNonNull(request.dueAt()).isBefore(Objects.requireNonNull(request.borrowedAt()))) {
            throw new BusinessRuleViolationException("Due date must be after borrowed date");
        }

        BorrowRecord borrowRecord = new BorrowRecord(borrowRequest, item, user, request.borrowedAt(), request.dueAt());
        borrowRequest.setStatus(BorrowRequestStatus.COMPLETED);
        item.setStatus(ItemStatus.BORROWED);
        itemRepository.save(item);
        borrowRequestRepository.save(borrowRequest);
        return borrowRecordRepository.save(borrowRecord);
    }

    public BorrowRecord getBorrowRecordById(Long id) {
        return borrowRecordRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Borrow record not found with id: " + id));
    }

    public BorrowRecord updateBorrowRecord(Long id, BorrowRecordCreateRequest request) {
        BorrowRecord existingRecord = getBorrowRecordById(id);
        BorrowRequest borrowRequest = borrowRequestRepository.findById(Objects.requireNonNull(request.borrowRequestId()))
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found with id: " + request.borrowRequestId()));
        Item item = itemRepository.findById(Objects.requireNonNull(request.itemId()))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + request.itemId()));
        User user = userRepository.findById(Objects.requireNonNull(request.borrowedByUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.borrowedByUserId()));

        existingRecord.setBorrowRequest(borrowRequest);
        existingRecord.setItem(item);
        existingRecord.setBorrowedBy(user);
        existingRecord.setBorrowedAt(request.borrowedAt());
        existingRecord.setDueAt(request.dueAt());
        return borrowRecordRepository.save(existingRecord);
    }

    public void deleteBorrowRecord(Long id) {
        BorrowRecord existingRecord = getBorrowRecordById(id);
        borrowRecordRepository.delete(Objects.requireNonNull(existingRecord));
    }

    public BorrowRecord returnBorrowedItem(Long id) {
        BorrowRecord record = getBorrowRecordById(id);
        if (record.isReturned()) {
            throw new BusinessRuleViolationException("Borrow record is already returned: " + record.getId());
        }

        record.setReturned(true);
        record.setReturnedAt(LocalDateTime.now());
        // Item is RETURNED; a future workflow step can reset it to AVAILABLE
        record.getItem().setStatus(ItemStatus.RETURNED);
        record.getBorrowRequest().setStatus(BorrowRequestStatus.COMPLETED);
        itemRepository.save(Objects.requireNonNull(record.getItem()));
        borrowRequestRepository.save(Objects.requireNonNull(record.getBorrowRequest()));
        return borrowRecordRepository.save(Objects.requireNonNull(record));
    }

    public List<BorrowRecord> markOverdueBorrowRecords(LocalDateTime now) {
        List<BorrowRecord> overdueRecords = borrowRecordRepository.findByReturnedFalseAndDueAtBefore(Objects.requireNonNull(now));
        overdueRecords.forEach(record -> record.getItem().setStatus(ItemStatus.OVERDUE));
        overdueRecords.forEach(record -> itemRepository.save(Objects.requireNonNull(record.getItem())));
        overdueRecords.forEach(record -> borrowRecordRepository.save(Objects.requireNonNull(record)));
        return overdueRecords;
    }

    public Page<BorrowRecord> searchBorrowRecords(Long itemId, Long borrowedByUserId, Boolean showOnlyActive, Boolean showOnlyOverdue, Pageable pageable) {
        return borrowRecordRepository.findAll(BorrowRecordSpecifications.build(itemId, borrowedByUserId, showOnlyActive, showOnlyOverdue), pageable);
    }
}