package com.borrowbox.service;

import com.borrowbox.dto.BorrowRequestCreateRequest;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowRequestServiceTest {

    @Mock
    private BorrowRequestRepository borrowRequestRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UserRepository userRepository;

    /**
     * Required by the BorrowRequestService constructor added in Day 7
     * for the confirmBorrowRequest method.
     */
    @Mock
    private BorrowRecordRepository borrowRecordRepository;

    @InjectMocks
    private BorrowRequestService borrowRequestService;

    // ─── createBorrowRequest ─────────────────────────────────

    @Test
    void createBorrowRequestRejectsArchivedItem() {
        Item item = new Item("Archived item", "desc");
        item.setId(1L);
        item.setArchived(true);
        item.setStatus(ItemStatus.ARCHIVED);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        BorrowRequestCreateRequest request = new BorrowRequestCreateRequest(1L, 2L, "please");

        assertThatThrownBy(() -> borrowRequestService.createBorrowRequest(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("archived item");
    }

    @Test
    @SuppressWarnings("null")
    void createBorrowRequestSavesWhenItemAvailable() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.AVAILABLE);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        doAnswer(invocation -> invocation.getArgument(0)).when(borrowRequestRepository).save(any(BorrowRequest.class));

        BorrowRequestCreateRequest request = new BorrowRequestCreateRequest(1L, 2L, "please");
        BorrowRequest saved = borrowRequestService.createBorrowRequest(request);

        assertThat(saved.getItem()).isEqualTo(item);
        assertThat(saved.getRequestedBy()).isEqualTo(user);
        assertThat(saved.getStatus()).isEqualTo(BorrowRequestStatus.PENDING);
    }

    @Test
    @SuppressWarnings("null")
    void createBorrowRequestSetsItemStatusToRequested() {
        Item item = new Item("Drill", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.AVAILABLE);
        User user = testUser("Bob", "bob@test.com");
        user.setId(2L);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        doAnswer(invocation -> invocation.getArgument(0)).when(borrowRequestRepository).save(any(BorrowRequest.class));

        borrowRequestService.createBorrowRequest(new BorrowRequestCreateRequest(1L, 2L, "msg"));

        assertThat(item.getStatus()).isEqualTo(ItemStatus.REQUESTED);
        verify(itemRepository).save(item);
    }

    @Test
    void createBorrowRequestRejectsUnavailableItem() {
        Item item = new Item("Borrowed item", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.BORROWED);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        BorrowRequestCreateRequest request = new BorrowRequestCreateRequest(1L, 2L, "please");

        assertThatThrownBy(() -> borrowRequestService.createBorrowRequest(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void createBorrowRequestRejectsSelfBorrow() {
        User alice = testUser("Alice", "alice@test.com");
        alice.setId(5L);
        Item item = new Item("Alice's drill", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.AVAILABLE);
        item.setOwner(alice);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(userRepository.findById(5L)).thenReturn(Optional.of(alice));

        BorrowRequestCreateRequest request = new BorrowRequestCreateRequest(1L, 5L, "I want my own item");

        assertThatThrownBy(() -> borrowRequestService.createBorrowRequest(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Cannot request your own item");
    }

    @Test
    @SuppressWarnings("null")
    void createBorrowRequestAllowsDifferentUserToBorrow() {
        User alice = testUser("Alice", "alice@test.com");
        alice.setId(5L);
        User bob = testUser("Bob", "bob@test.com");
        bob.setId(6L);
        Item item = new Item("Alice's drill", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.AVAILABLE);
        item.setOwner(alice);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(userRepository.findById(6L)).thenReturn(Optional.of(bob));
        doAnswer(invocation -> invocation.getArgument(0)).when(borrowRequestRepository).save(any(BorrowRequest.class));

        BorrowRequest saved = borrowRequestService.createBorrowRequest(
                new BorrowRequestCreateRequest(1L, 6L, "Can I borrow?"));

        assertThat(saved.getRequestedBy()).isEqualTo(bob);
        assertThat(saved.getStatus()).isEqualTo(BorrowRequestStatus.PENDING);
    }

    // ─── approveBorrowRequest ────────────────────────────────

    @Test
    void approveBorrowRequestUpdatesStatus() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        // Item must be REQUESTED — the state set by createBorrowRequest before approve is called
        item.setStatus(ItemStatus.REQUESTED);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "message");
        request.setId(3L);

        when(borrowRequestRepository.findById(3L)).thenReturn(Optional.of(request));
        when(itemRepository.save(item)).thenReturn(item);
        when(borrowRequestRepository.save(request)).thenReturn(request);

        BorrowRequest approved = borrowRequestService.approveBorrowRequest(3L);

        assertThat(approved.getStatus()).isEqualTo(BorrowRequestStatus.APPROVED);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.APPROVED);
    }

    @Test
    void approveBorrowRequestRejectsNonPendingRequest() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.REQUESTED);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "message");
        request.setId(3L);
        request.setStatus(BorrowRequestStatus.APPROVED);

        when(borrowRequestRepository.findById(3L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> borrowRequestService.approveBorrowRequest(3L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Only pending requests can be approved");
    }

    // ─── rejectBorrowRequest ─────────────────────────────────

    @Test
    void rejectBorrowRequestSetsRejectedAndRestoresItem() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.REQUESTED);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "message");
        request.setId(3L);

        when(borrowRequestRepository.findById(3L)).thenReturn(Optional.of(request));
        when(itemRepository.save(item)).thenReturn(item);
        when(borrowRequestRepository.save(request)).thenReturn(request);

        BorrowRequest rejected = borrowRequestService.rejectBorrowRequest(3L);

        assertThat(rejected.getStatus()).isEqualTo(BorrowRequestStatus.REJECTED);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.AVAILABLE);
    }

    @Test
    void rejectBorrowRequestRejectsNonPendingRequest() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "message");
        request.setId(3L);
        request.setStatus(BorrowRequestStatus.APPROVED);

        when(borrowRequestRepository.findById(3L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> borrowRequestService.rejectBorrowRequest(3L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Only pending requests can be rejected");
    }

    // ─── confirmBorrowRequest ────────────────────────────────

    @Test
    @SuppressWarnings("null")
    void confirmBorrowRequestApprovesAndCreatesBorrowRecord() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.REQUESTED);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "message");
        request.setId(3L);

        LocalDateTime dueAt = LocalDateTime.now().plusDays(14);

        when(borrowRequestRepository.findById(3L)).thenReturn(Optional.of(request));
        when(itemRepository.save(item)).thenReturn(item);
        when(borrowRequestRepository.save(request)).thenReturn(request);
        doAnswer(invocation -> invocation.getArgument(0)).when(borrowRecordRepository).save(any(BorrowRecord.class));

        BorrowRecord record = borrowRequestService.confirmBorrowRequest(3L, dueAt);

        // Request ends up COMPLETED, item ends up BORROWED
        assertThat(request.getStatus()).isEqualTo(BorrowRequestStatus.COMPLETED);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.BORROWED);
        // Record is created with correct fields
        assertThat(record).isNotNull();
        assertThat(record.getItem()).isEqualTo(item);
        assertThat(record.getBorrowedBy()).isEqualTo(user);
        assertThat(record.getDueAt()).isEqualTo(dueAt);
        assertThat(record.isReturned()).isFalse();
    }

    @Test
    void confirmBorrowRequestRejectsNonPendingRequest() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("Test User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "message");
        request.setId(3L);
        request.setStatus(BorrowRequestStatus.REJECTED);

        when(borrowRequestRepository.findById(3L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> borrowRequestService.confirmBorrowRequest(3L, LocalDateTime.now().plusDays(7)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Only pending requests can be confirmed");
    }

    // ─── Helpers ─────────────────────────────────────────────

    private User testUser(String fullName, String email) {
        User user = new User(fullName, email);
        user.setPasswordHash("test-password");
        return user;
    }
}