package com.borrowbox.integration;

import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ItemRepositoryIntegrationTest {

    @Autowired
    private ItemRepository itemRepository;

    @Test
    void saveAndLoadItemFromMySql() {
        Item item = new Item("Test Item", "Integration test item");
        Item saved = itemRepository.save(item);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ItemStatus.AVAILABLE);

        Item loaded = itemRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo("Test Item");
        assertThat(loaded.getDescription()).isEqualTo("Integration test item");
        assertThat(loaded.getStatus()).isEqualTo(ItemStatus.AVAILABLE);
    }

    @Test
    void searchWithNoStatusReturnsAllNonArchivedItems() {
        Item availableItem = itemRepository.save(new Item("Active Item", "Available desc"));
        
        Item borrowedItem = new Item("Borrowed Item", "Borrowed desc");
        borrowedItem.setStatus(ItemStatus.BORROWED);
        itemRepository.save(borrowedItem);

        Item archivedItem = new Item("Archived Item", "Archived desc");
        archivedItem.setArchived(true);
        archivedItem.setStatus(ItemStatus.ARCHIVED);
        itemRepository.save(archivedItem);

        var spec = com.borrowbox.spec.ItemSpecifications.build(null, null, null, null, null);
        var results = itemRepository.findAll(spec);

        assertThat(results).extracting(Item::getTitle)
                .contains(availableItem.getTitle(), borrowedItem.getTitle())
                .doesNotContain(archivedItem.getTitle());
    }

    @Test
    void searchWithAvailableStatusReturnsOnlyAvailable() {
        Item availableItem = itemRepository.save(new Item("Drill", "Cordless drill"));
        
        Item borrowedItem = new Item("Ladder", "Step ladder");
        borrowedItem.setStatus(ItemStatus.BORROWED);
        itemRepository.save(borrowedItem);

        var spec = com.borrowbox.spec.ItemSpecifications.build(null, ItemStatus.AVAILABLE, null, null, null);
        var results = itemRepository.findAll(spec);

        assertThat(results).extracting(Item::getTitle)
                .contains(availableItem.getTitle())
                .doesNotContain(borrowedItem.getTitle());
    }
}
