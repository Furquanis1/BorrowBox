package com.borrowbox.service;

import com.borrowbox.dto.BorrowRecordCreateRequest;
import com.borrowbox.entity.BorrowRecord;
import com.borrowbox.entity.BorrowRequest;
import com.borrowbox.entity.BorrowRequestStatus;
import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.repository.BorrowRecordRepository;
import com.borrowbox.repository.BorrowRequestRepository;
import com.borrowbox.repository.ItemRepository;
import com.borrowbox.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowRecordServiceTest {

    @Mock
    private BorrowRecordRepository borrowRecordRepository;

    @Mock
    private BorrowRequestRepository borrowRequestRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BorrowRecordService borrowRecordService;

    @Test
    void createBorrowRecordRejectsUnapprovedRequest() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.APPROVED);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "message");
        request.setId(3L);
        request.setStatus(BorrowRequestStatus.PENDING);

        when(borrowRequestRepository.findById(3L)).thenReturn(Optional.of(request));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        BorrowRecordCreateRequest createRequest = new BorrowRecordCreateRequest(3L, 1L, 2L, LocalDateTime.now(), LocalDateTime.now().plusDays(7));

        assertThatThrownBy(() -> borrowRecordService.createBorrowRecord(createRequest))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("approved");
    }

    @Test
    @SuppressWarnings("null")
    void createBorrowRecordSetsBorrowedState() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.APPROVED);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "message");
        request.setId(3L);
        request.setStatus(BorrowRequestStatus.APPROVED);

        when(borrowRequestRepository.findById(3L)).thenReturn(Optional.of(request));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(itemRepository.save(item)).thenReturn(item);
        when(borrowRequestRepository.save(request)).thenReturn(request);
        doAnswer(invocation -> invocation.getArgument(0)).when(borrowRecordRepository).save(any(BorrowRecord.class));

        BorrowRecordCreateRequest createRequest = new BorrowRecordCreateRequest(3L, 1L, 2L, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        BorrowRecord saved = borrowRecordService.createBorrowRecord(createRequest);

        assertThat(saved.isReturned()).isFalse();
        assertThat(saved.getBorrowRequest().getStatus()).isEqualTo(BorrowRequestStatus.COMPLETED);
        assertThat(saved.getItem().getStatus()).isEqualTo(ItemStatus.BORROWED);
    }

    @Test
    @SuppressWarnings("null")
    void returnBorrowedItemMarksReturned() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.BORROWED);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "message");
        request.setId(3L);
        request.setStatus(BorrowRequestStatus.COMPLETED);
        BorrowRecord record = new BorrowRecord(request, item, user, LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(5));
        record.setId(4L);

        when(borrowRecordRepository.findById(4L)).thenReturn(Optional.of(record));
        when(itemRepository.save(item)).thenReturn(item);
        when(borrowRequestRepository.save(request)).thenReturn(request);
        when(borrowRecordRepository.save(record)).thenReturn(record);

        BorrowRecord returned = borrowRecordService.returnBorrowedItem(4L);

        assertThat(returned.isReturned()).isTrue();
        assertThat(returned.getReturnedAt()).isNotNull();
        assertThat(returned.getItem().getStatus()).isEqualTo(ItemStatus.RETURNED);
    }

    @Test
    @SuppressWarnings("null")
    void markOverdueBorrowRecordsMarksItemsOverdue() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.BORROWED);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "message");
        request.setId(3L);
        request.setStatus(BorrowRequestStatus.COMPLETED);
        BorrowRecord record = new BorrowRecord(request, item, user, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1));
        record.setId(4L);

        when(borrowRecordRepository.findByReturnedFalseAndDueAtBefore(any())).thenReturn(Collections.singletonList(record));
        doAnswer(invocation -> invocation.getArgument(0)).when(itemRepository).save(any(Item.class));
        doAnswer(invocation -> invocation.getArgument(0)).when(borrowRecordRepository).save(any(BorrowRecord.class));

        borrowRecordService.markOverdueBorrowRecords(LocalDateTime.now());

        assertThat(item.getStatus()).isEqualTo(ItemStatus.OVERDUE);
    }

    private User testUser(String fullName, String email) {
        User user = new User(fullName, email);
        user.setPasswordHash("test-password");
        return user;
    }
}