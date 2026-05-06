package com.borrowbox.service;

import com.borrowbox.dto.BorrowRequestCreateRequest;
import com.borrowbox.entity.BorrowRequest;
import com.borrowbox.entity.BorrowRequestStatus;
import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.repository.BorrowRequestRepository;
import com.borrowbox.repository.ItemRepository;
import com.borrowbox.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowRequestServiceTest {

    @Mock
    private BorrowRequestRepository borrowRequestRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BorrowRequestService borrowRequestService;

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
    void approveBorrowRequestUpdatesStatus() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.AVAILABLE);
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

    private User testUser(String fullName, String email) {
        User user = new User(fullName, email);
        user.setPasswordHash("test-password");
        return user;
    }
}