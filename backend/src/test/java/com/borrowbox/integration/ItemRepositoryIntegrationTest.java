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
}
