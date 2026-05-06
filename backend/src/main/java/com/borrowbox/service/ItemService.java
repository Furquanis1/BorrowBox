package com.borrowbox.service;

import com.borrowbox.dto.ItemCreateRequest;
import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.BorrowRecordRepository;
import com.borrowbox.repository.ItemRepository;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Objects;
import com.borrowbox.spec.ItemSpecifications;
import com.borrowbox.entity.ItemStatus;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final BorrowRecordRepository borrowRecordRepository;

    public ItemService(ItemRepository itemRepository, BorrowRecordRepository borrowRecordRepository) {
        this.itemRepository = itemRepository;
        this.borrowRecordRepository = borrowRecordRepository;
    }

    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    public Page<Item> searchItems(String q, ItemStatus status, Long categoryId, Long groupId, Long ownerId, Pageable pageable) {
        return itemRepository.findAll(ItemSpecifications.build(q, status, categoryId, groupId, ownerId), pageable);
    }

    public Item createItem(ItemCreateRequest request) {
        Item item = new Item(request.title(), request.description());
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
}
