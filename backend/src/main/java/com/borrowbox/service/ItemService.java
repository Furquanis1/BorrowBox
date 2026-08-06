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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Objects;
import com.borrowbox.spec.ItemSpecifications;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final UserRepository userRepository;

    public ItemService(ItemRepository itemRepository,
                       BorrowRecordRepository borrowRecordRepository,
                       UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.userRepository = userRepository;
    }

    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    public Page<Item> searchItems(String q, ItemStatus status, Long categoryId, Long groupId, Long ownerId, Pageable pageable) {
        return itemRepository.findAll(ItemSpecifications.build(q, status, categoryId, groupId, ownerId), pageable);
    }

    /**
     * Creates an item and assigns the currently authenticated user as the owner.
     * The owner is resolved from the JWT cookie via the SecurityContext.
     */
    public Item createItem(ItemCreateRequest request) {
        Item item = new Item(request.title(), request.description());

        User owner = resolveCurrentUser();
        if (owner != null) {
            item.setOwner(owner);
        }

        return itemRepository.save(item);
    }

    public Item getItemById(Long id) {
        return itemRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + id));
    }

    public Item updateItem(Long id, ItemCreateRequest request) {
        Item existingItem = itemRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + id));

        existingItem.setTitle(request.title());
        existingItem.setDescription(request.description());

        return itemRepository.save(existingItem);
    }

    public void deleteItem(Long id) {
        Item existingItem = itemRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + id));
        itemRepository.delete(Objects.requireNonNull(existingItem));
    }

    public Item archiveItem(Long id) {
        Item existingItem = itemRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + id));

        if (existingItem.isArchived()) {
            throw new BusinessRuleViolationException("Item is already archived: " + id);
        }

        if (borrowRecordRepository.existsByItemIdAndReturnedFalse(id)) {
            throw new BusinessRuleViolationException("Cannot archive an item with an active borrow record: " + id);
        }

        existingItem.setArchived(true);
        existingItem.setStatus(ItemStatus.ARCHIVED);
        return itemRepository.save(existingItem);
    }

    /** Resolves the authenticated user from the Spring SecurityContext. */
    private User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String email = auth.getName(); // JwtAuthenticationFilter stores email as username
        return userRepository.findByEmail(email).orElse(null);
    }
}
