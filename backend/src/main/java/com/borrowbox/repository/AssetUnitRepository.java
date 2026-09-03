package com.borrowbox.repository;

import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetUnitRepository extends JpaRepository<AssetUnit, Long> {

    List<AssetUnit> findByAssetId(Long assetId);

    long countByAssetIdAndStatusNot(Long assetId, AssetUnitStatus status);

    long countByAssetIdAndStatus(Long assetId, AssetUnitStatus status);
}
