package com.borrowbox.repository;

import com.borrowbox.entity.AssetUnitConditionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetUnitConditionHistoryRepository extends JpaRepository<AssetUnitConditionHistory, Long> {

    /**
     * V2.5.3: the full recorded chain for one unit, oldest first. occurredAt is
     * the chronological key; id breaks server-clock ties the same way the
     * Evidence list does, so the chain a reader sees is always reproducible and
     * always ends at the unit's current condition.
     */
    List<AssetUnitConditionHistory> findByAssetUnitIdOrderByOccurredAtAscIdAsc(Long assetUnitId);
}
