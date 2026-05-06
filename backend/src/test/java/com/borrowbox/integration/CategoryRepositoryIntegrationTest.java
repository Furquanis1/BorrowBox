package com.borrowbox.integration;

import com.borrowbox.entity.Category;
import com.borrowbox.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CategoryRepositoryIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void saveAndLoadCategoryFromMySql() {
        Category category = new Category("Test Category", "Integration test category");
        Category saved = categoryRepository.save(category);

        assertThat(saved.getId()).isNotNull();

        Category loaded = categoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Test Category");
        assertThat(loaded.getDescription()).isEqualTo("Integration test category");
    }
}
