package com.borrowbox.repository;

import com.borrowbox.entity.WaitlistEntry;
import com.borrowbox.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    /**
     * V2.2.7 pessimistic head-selection query: the first WAITING entry for an
     * asset in server-authoritative order (created_at ASC, id ASC), locked with
     * FOR UPDATE so concurrent releases cannot both promote the same waiter.
     * Always acquired AFTER the AssetUnit availability lock so the lock order
     * is never inverted. The LIMIT 1 is enforced by the database so exactly
     * one row is locked even when multiple WAITING entries exist.
     */
    @Query(value = "SELECT * FROM waitlist_entries e " +
            "WHERE e.asset_id = :assetId AND e.status = 'WAITING' " +
            "ORDER BY e.created_at ASC, e.id ASC " +
            "LIMIT 1 FOR UPDATE", nativeQuery = true)
    Optional<WaitlistEntry> findFirstWaitingForUpdate(@Param("assetId") Long assetId);

    List<WaitlistEntry> findByBorrowerIdAndStatusOrderByCreatedAtAscIdAsc(Long borrowerId, WaitlistStatus status);

    Optional<WaitlistEntry> findByIdAndBorrowerId(Long id, Long borrowerId);

    boolean existsByAssetIdAndBorrowerId(Long assetId, Long borrowerId);

    List<WaitlistEntry> findByAssetId(Long assetId);

    long countByAssetIdAndStatus(Long assetId, WaitlistStatus status);

    /**
     * V2.2.7 position derivation: number of WAITING entries for an asset that
     * sort strictly before this row (position = countWaitingBefore + 1).
     * Derived on read; never persisted.
     */
    @Query("select count(e) from WaitlistEntry e " +
            "where e.asset.id = :assetId and e.status = com.borrowbox.entity.WaitlistStatus.WAITING " +
            "and (e.createdAt < :createdAt or (e.createdAt = :createdAt and e.id < :id))")
    long countWaitingBefore(@Param("assetId") Long assetId,
                            @Param("createdAt") LocalDateTime createdAt,
                            @Param("id") Long id);
}
