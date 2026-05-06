package com.borrowbox.spec;

import com.borrowbox.entity.BorrowRecord;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class BorrowRecordSpecifications {

    private BorrowRecordSpecifications() {}

    public static Specification<BorrowRecord> itemIdEquals(Long itemId) {
        return (root, query, cb) -> itemId == null ? null : cb.equal(root.get("item").get("id"), itemId);
    }

    public static Specification<BorrowRecord> borrowedByIdEquals(Long borrowedByUserId) {
        return (root, query, cb) -> borrowedByUserId == null ? null : cb.equal(root.get("borrowedByUser").get("id"), borrowedByUserId);
    }

    public static Specification<BorrowRecord> returnedFalse() {
        return (root, query, cb) -> cb.isFalse(root.get("returned"));
    }

    public static Specification<BorrowRecord> overdueOnly() {
        return (root, query, cb) -> cb.and(
                cb.isFalse(root.get("returned")),
                cb.lessThan(root.get("dueAt"), LocalDateTime.now())
        );
    }

    public static Specification<BorrowRecord> build(Long itemId, Long borrowedByUserId, Boolean showOnlyActive, Boolean showOnlyOverdue) {
        Specification<BorrowRecord> spec = (root, query, cb) -> null;
        spec = spec.and(itemIdEquals(itemId));
        spec = spec.and(borrowedByIdEquals(borrowedByUserId));

        if (Boolean.TRUE.equals(showOnlyOverdue)) {
            spec = spec.and(overdueOnly());
        } else if (Boolean.TRUE.equals(showOnlyActive)) {
            spec = spec.and(returnedFalse());
        }

        return spec;
    }
}
