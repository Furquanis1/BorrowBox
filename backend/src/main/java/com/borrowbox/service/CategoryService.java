package com.borrowbox.service;

import com.borrowbox.dto.CategoryCreateRequest;
import com.borrowbox.entity.Category;
import com.borrowbox.exception.ForeignKeyDeleteConflictTranslator;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.CategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ForeignKeyDeleteConflictTranslator deleteConflictTranslator;

    public CategoryService(CategoryRepository categoryRepository,
                           ForeignKeyDeleteConflictTranslator deleteConflictTranslator) {
        this.categoryRepository = categoryRepository;
        this.deleteConflictTranslator = deleteConflictTranslator;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category createCategory(CategoryCreateRequest request) {
        Category category = new Category(request.name(), request.description());
        return categoryRepository.save(category);
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    public Category updateCategory(Long id, CategoryCreateRequest request) {
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        existingCategory.setName(request.name());
        existingCategory.setDescription(request.description());

        return categoryRepository.save(existingCategory);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        try {
            categoryRepository.delete(existingCategory);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw deleteConflictTranslator.translate(ex);
        }
    }
}
