package com.borrowbox.repository;

import com.borrowbox.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByIdAndOwnerId(Long id, Long ownerId);

    List<Asset> findByOwnerId(Long ownerId);
}
