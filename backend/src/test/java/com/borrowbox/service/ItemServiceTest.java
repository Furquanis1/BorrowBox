package com.borrowbox.service;

import com.borrowbox.dto.ItemCreateRequest;
import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.BorrowRecordRepository;
import com.borrowbox.repository.ItemRepository;
import com.borrowbox.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private BorrowRecordRepository borrowRecordRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ItemService itemService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ─── createItem ──────────────────────────────────────────

    @Test
    @SuppressWarnings("null")
    void createItemSavesWithoutOwnerWhenNoAuth() {
        doAnswer(inv -> inv.getArgument(0)).when(itemRepository).save(any(Item.class));

        Item created = itemService.createItem(new ItemCreateRequest("Drill", "A cordless drill"));

        assertThat(created.getTitle()).isEqualTo("Drill");
        assertThat(created.getOwner()).isNull();
        verify(itemRepository).save(any(Item.class));
    }

    @Test
    @SuppressWarnings("null")
    void createItemAssignsOwnerFromSecurityContext() {
        User alice = testUser("Alice", "alice@test.com");
        alice.setId(5L);

        // Simulate an authenticated user
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@test.com", null, List.of()));
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(alice));
        doAnswer(inv -> inv.getArgument(0)).when(itemRepository).save(any(Item.class));

        Item created = itemService.createItem(new ItemCreateRequest("Drill", "desc"));

        assertThat(created.getOwner()).isEqualTo(alice);
    }

    // ─── getItemById ─────────────────────────────────────────

    @Test
    void getItemByIdReturnsItem() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        Item found = itemService.getItemById(1L);
        assertThat(found.getId()).isEqualTo(1L);
    }

    @Test
    void getItemByIdThrowsWhenNotFound() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getItemById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ─── archiveItem ─────────────────────────────────────────

    @Test
    void archiveItemSetsArchivedAndStatus() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(borrowRecordRepository.existsByItemIdAndReturnedFalse(1L)).thenReturn(false);
        doAnswer(inv -> inv.getArgument(0)).when(itemRepository).save(any(Item.class));

        Item archived = itemService.archiveItem(1L);

        assertThat(archived.isArchived()).isTrue();
        assertThat(archived.getStatus()).isEqualTo(ItemStatus.ARCHIVED);
    }

    @Test
    void archiveItemRejectsAlreadyArchivedItem() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setArchived(true);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.archiveItem(1L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already archived");
    }

    @Test
    void archiveItemRejectsItemWithActiveBorrowRecord() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(borrowRecordRepository.existsByItemIdAndReturnedFalse(1L)).thenReturn(true);

        assertThatThrownBy(() -> itemService.archiveItem(1L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("active borrow record");
    }

    // ─── updateItem ──────────────────────────────────────────

    @Test
    void updateItemChangesFields() {
        Item item = new Item("Old Title", "old desc");
        item.setId(1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        doAnswer(inv -> inv.getArgument(0)).when(itemRepository).save(any(Item.class));

        Item updated = itemService.updateItem(1L, new ItemCreateRequest("New Title", "new desc"));

        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getDescription()).isEqualTo("new desc");
    }

    @Test
    void updateItemThrowsWhenNotFound() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.updateItem(99L, new ItemCreateRequest("T", "D")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ─── deleteItem ──────────────────────────────────────────

    @Test
    void deleteItemCallsRepository() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        itemService.deleteItem(1L);

        verify(itemRepository).delete(item);
    }

    @Test
    void deleteItemThrowsWhenNotFound() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.deleteItem(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getAllItems (paginated) ──────────────────────────────

    @Test
    void getAllItemsPageableReturnsPage() {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Item> page = new PageImpl<>(List.of(item), pageable, 1);
        when(itemRepository.findAll(pageable)).thenReturn(page);

        Page<Item> result = itemService.getAllItems(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Book");
    }

    // ─── searchItems (spec + pageable) ───────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void searchItemsDelegatesToRepository() {
        Item item = new Item("Drill", "Power drill");
        item.setId(2L);
        Pageable pageable = PageRequest.of(0, 5);
        Page<Item> page = new PageImpl<>(List.of(item), pageable, 1);
        when(itemRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<Item> result = itemService.searchItems("drill", null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Drill");
    }

    // ─── Helper ──────────────────────────────────────────────

    private User testUser(String fullName, String email) {
        User user = new User(fullName, email);
        user.setPasswordHash("test-password");
        return user;
    }
}
