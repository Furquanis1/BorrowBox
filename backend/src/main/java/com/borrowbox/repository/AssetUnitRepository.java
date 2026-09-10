package com.borrowbox.repository;

import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetUnitRepository extends JpaRepository<AssetUnit, Long> {

    List<AssetUnit> findByAssetId(Long assetId);

    long countByAssetIdAndStatusNot(Long assetId, AssetUnitStatus status);

    long countByAssetIdAndStatus(Long assetId, AssetUnitStatus status);

    /**
     * Locks a single AVAILABLE unit of an asset with PESSIMISTIC_WRITE, so
     * concurrent request creations cannot both pick the same unit: the
     * database lock acquisition order decides the winner, and the loser sees
     * the unit as RESERVED when it re-checks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AssetUnit u where u.asset.id = :assetId and u.status = com.borrowbox.entity.AssetUnitStatus.AVAILABLE order by u.id asc")
    Optional<AssetUnit> findFirstByAssetIdAndStatusForUpdate(@Param("assetId") Long assetId);
}
