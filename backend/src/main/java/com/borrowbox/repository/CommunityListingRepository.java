package com.borrowbox.repository;

import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityListingRepository extends JpaRepository<CommunityListing, Long> {

    Optional<CommunityListing> findByAssetIdAndCommunityId(Long assetId, Long communityId);

    List<CommunityListing> findByAssetId(Long assetId);

    List<CommunityListing> findByCommunityIdAndListingStatus(Long communityId, ListingStatus listingStatus);
}