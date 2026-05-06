package com.borrowbox.integration;

import com.borrowbox.dto.BorrowRecordCreateRequest;
import com.borrowbox.dto.BorrowRequestCreateRequest;
import com.borrowbox.entity.BorrowRecord;
import com.borrowbox.entity.BorrowRequest;
import com.borrowbox.entity.BorrowRequestStatus;
import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.entity.User;
import com.borrowbox.repository.BorrowRecordRepository;
import com.borrowbox.repository.BorrowRequestRepository;
import com.borrowbox.repository.ItemRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.BorrowRecordService;
import com.borrowbox.service.BorrowRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BorrowWorkflowIntegrationTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BorrowRequestRepository borrowRequestRepository;

    @Autowired
    private BorrowRecordRepository borrowRecordRepository;

    @Autowired
    private BorrowRequestService borrowRequestService;

    @Autowired
    private BorrowRecordService borrowRecordService;

    @Test
    void fullBorrowLifecyclePersistsAcrossRepositories() {
                User borrower = userRepository.save(testUser("Integration Borrower", "borrower@example.com"));
        Item item = itemRepository.save(new Item("Integration Item", "Integration item description"));

        BorrowRequest createdRequest = borrowRequestService.createBorrowRequest(
                new BorrowRequestCreateRequest(item.getId(), borrower.getId(), "Need this item for testing"));

        assertThat(createdRequest.getId()).isNotNull();
        assertThat(createdRequest.getStatus()).isEqualTo(BorrowRequestStatus.PENDING);

        BorrowRequest approvedRequest = borrowRequestService.approveBorrowRequest(createdRequest.getId());
        assertThat(approvedRequest.getStatus()).isEqualTo(BorrowRequestStatus.APPROVED);
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.APPROVED);
        assertThat(borrowRequestRepository.findById(createdRequest.getId()).orElseThrow().getStatus()).isEqualTo(BorrowRequestStatus.APPROVED);

        LocalDateTime borrowedAt = LocalDateTime.now().minusDays(1);
        LocalDateTime dueAt = borrowedAt.plusDays(7);
        BorrowRecord createdRecord = borrowRecordService.createBorrowRecord(
                new BorrowRecordCreateRequest(createdRequest.getId(), item.getId(), borrower.getId(), borrowedAt, dueAt));

        assertThat(createdRecord.getId()).isNotNull();
        assertThat(createdRecord.isReturned()).isFalse();
        assertThat(borrowRecordRepository.findById(createdRecord.getId()).orElseThrow().isReturned()).isFalse();
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.BORROWED);
        assertThat(borrowRequestRepository.findById(createdRequest.getId()).orElseThrow().getStatus()).isEqualTo(BorrowRequestStatus.COMPLETED);

        BorrowRecord returnedRecord = borrowRecordService.returnBorrowedItem(createdRecord.getId());
        assertThat(returnedRecord.isReturned()).isTrue();
        assertThat(returnedRecord.getReturnedAt()).isNotNull();
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.RETURNED);
        assertThat(borrowRecordRepository.findById(createdRecord.getId()).orElseThrow().isReturned()).isTrue();
    }

    @Test
    void overdueBorrowRecordsArePersistedToMysql() {
                User borrower = userRepository.save(testUser("Overdue Borrower", "overdue@example.com"));
        Item item = itemRepository.save(new Item("Overdue Item", "Overdue item description"));

        BorrowRequest request = borrowRequestService.createBorrowRequest(
                new BorrowRequestCreateRequest(item.getId(), borrower.getId(), "Loan me"));
        borrowRequestService.approveBorrowRequest(request.getId());

        LocalDateTime borrowedAt = LocalDateTime.now().minusDays(14);
        LocalDateTime dueAt = LocalDateTime.now().minusDays(7);
        BorrowRecord record = borrowRecordService.createBorrowRecord(
                new BorrowRecordCreateRequest(request.getId(), item.getId(), borrower.getId(), borrowedAt, dueAt));

        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.BORROWED);
        assertThat(record.isReturned()).isFalse();

        borrowRecordService.markOverdueBorrowRecords(LocalDateTime.now());

        Item reloadedItem = itemRepository.findById(item.getId()).orElseThrow();
        BorrowRecord reloadedRecord = borrowRecordRepository.findById(record.getId()).orElseThrow();

        assertThat(reloadedItem.getStatus()).isEqualTo(ItemStatus.OVERDUE);
        assertThat(reloadedRecord.isReturned()).isFalse();
        assertThat(reloadedRecord.getDueAt()).isBefore(LocalDateTime.now());
    }

        private User testUser(String fullName, String email) {
                User user = new User(fullName, email);
                user.setPasswordHash("test-password");
                return user;
        }
}
