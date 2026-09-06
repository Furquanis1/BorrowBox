package com.borrowbox.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * A CommunityListing determines where an owned Asset is offered inside a
 * Community. It has no independent quantity: availability always comes from
 * the shared AssetUnit pool of the asset.
 *
 * State is soft: LISTED / UNLISTED. A listing row is never deleted; an
 * UNLISTED row is reactivated by creating the same (asset, community) pair
 * again.
 */
@Entity
@Table(name = "community_listings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_asset_community",
                columnNames = {"asset_id", "community_id"}
        ))
public class CommunityListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_status", nullable = false)
    private ListingStatus listingStatus = ListingStatus.LISTED;

    @Column(name = "listed_at", nullable = false, updatable = false)
    private LocalDateTime listedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public CommunityListing() {
    }

    @PrePersist
    protected void onCreate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public Community getCommunity() {
        return community;
    }

    public void setCommunity(Community community) {
        this.community = community;
    }

    public ListingStatus getListingStatus() {
        return listingStatus;
    }

    public void setListingStatus(ListingStatus listingStatus) {
        this.listingStatus = listingStatus;
    }

    public LocalDateTime getListedAt() {
        return listedAt;
    }

    public void setListedAt(LocalDateTime listedAt) {
        this.listedAt = listedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}