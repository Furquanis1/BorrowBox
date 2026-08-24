package com.borrowbox.spec;

import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import org.springframework.data.jpa.domain.Specification;

public final class ItemSpecifications {

    private ItemSpecifications() {}

    public static Specification<Item> titleContains(String q) {
        return (root, query, cb) -> q == null || q.isBlank() ? null : cb.like(cb.lower(root.get("title")), "%" + q.toLowerCase() + "%");
    }

    public static Specification<Item> hasStatus(ItemStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Item> hasCategoryId(Long categoryId) {
        return (root, query, cb) -> categoryId == null ? null : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Item> hasGroupId(Long groupId) {
        return (root, query, cb) -> groupId == null ? null : cb.equal(root.get("group").get("id"), groupId);
    }

    public static Specification<Item> hasOwnerId(Long ownerId) {
        return (root, query, cb) -> ownerId == null ? null : cb.equal(root.get("owner").get("id"), ownerId);
    }

    public static Specification<Item> notArchived() {
        return (root, query, cb) -> cb.isFalse(root.get("archived"));
    }

    public static Specification<Item> build(String q, ItemStatus status, Long categoryId, Long groupId, Long ownerId) {
        Specification<Item> spec = Specification.where(null);
        if (q != null && !q.isBlank()) {
            spec = spec.and(titleContains(q));
        }
        if (status != null) {
            spec = spec.and(hasStatus(status));
        } else if (ownerId == null) {
            // General explore search without status filter returns non-archived items
            spec = spec.and(notArchived());
        }
        if (categoryId != null) {
            spec = spec.and(hasCategoryId(categoryId));
        }
        if (groupId != null) {
            spec = spec.and(hasGroupId(groupId));
        }
        if (ownerId != null) {
            spec = spec.and(hasOwnerId(ownerId));
        }
        return spec;
    }
}
