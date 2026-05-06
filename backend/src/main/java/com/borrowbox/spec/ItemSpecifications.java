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

    public static Specification<Item> build(String q, ItemStatus status, Long categoryId, Long groupId, Long ownerId) {
        Specification<Item> specification = (root, query, cb) -> null;
        specification = specification.and(titleContains(q));
        specification = specification.and(hasStatus(status));
        specification = specification.and(hasCategoryId(categoryId));
        specification = specification.and(hasGroupId(groupId));
        specification = specification.and(hasOwnerId(ownerId));
        return specification;
    }
}
