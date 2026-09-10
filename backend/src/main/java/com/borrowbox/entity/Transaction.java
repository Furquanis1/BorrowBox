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

import java.time.LocalDateTime;

/**
 * A Transaction is one structured negotiation record for (listing, borrower,
 * lender). It reserves exactly one physical AssetUnit while any open
 * negotiation (PENDING / COUNTER_OFFERED / APPROVED) holds it.
 *
 * Immutable context (set at create, never mutated): borrower, lender,
 * community, listing, asset, reservedUnit, reservedAt, purpose,
 * requestedDurationDays. Everything else is negotiation state.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private CommunityListing listing;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private User borrower;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_id", nullable = false)
    private User lender;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserved_unit_id")
    private AssetUnit reservedUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus state = TransactionStatus.PENDING;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "requested_duration_days", nullable = false)
    private Integer requestedDurationDays;

    @Column(name = "counter_purpose")
    private String counterPurpose;

    @Column(name = "counter_duration_days")
    private Integer counterDurationDays;

    @Column(name = "counter_note")
    private String counterNote;

    @Column(name = "counter_offered_at")
    private LocalDateTime counterOfferedAt;

    @Column(name = "agreed_purpose")
    private String agreedPurpose;

    @Column(name = "agreed_duration_days")
    private Integer agreedDurationDays;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Transaction() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
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

    public Community getCommunity() {
        return community;
    }

    public void setCommunity(Community community) {
        this.community = community;
    }

    public CommunityListing getListing() {
        return listing;
    }

    public void setListing(CommunityListing listing) {
        this.listing = listing;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public User getBorrower() {
        return borrower;
    }

    public void setBorrower(User borrower) {
        this.borrower = borrower;
    }

    public User getLender() {
        return lender;
    }

    public void setLender(User lender) {
        this.lender = lender;
    }

    public AssetUnit getReservedUnit() {
        return reservedUnit;
    }

    public void setReservedUnit(AssetUnit reservedUnit) {
        this.reservedUnit = reservedUnit;
    }

    public TransactionStatus getState() {
        return state;
    }

    public void setState(TransactionStatus state) {
        this.state = state;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public Integer getRequestedDurationDays() {
        return requestedDurationDays;
    }

    public void setRequestedDurationDays(Integer requestedDurationDays) {
        this.requestedDurationDays = requestedDurationDays;
    }

    public String getCounterPurpose() {
        return counterPurpose;
    }

    public void setCounterPurpose(String counterPurpose) {
        this.counterPurpose = counterPurpose;
    }

    public Integer getCounterDurationDays() {
        return counterDurationDays;
    }

    public void setCounterDurationDays(Integer counterDurationDays) {
        this.counterDurationDays = counterDurationDays;
    }

    public String getCounterNote() {
        return counterNote;
    }

    public void setCounterNote(String counterNote) {
        this.counterNote = counterNote;
    }

    public LocalDateTime getCounterOfferedAt() {
        return counterOfferedAt;
    }

    public void setCounterOfferedAt(LocalDateTime counterOfferedAt) {
        this.counterOfferedAt = counterOfferedAt;
    }

    public String getAgreedPurpose() {
        return agreedPurpose;
    }

    public void setAgreedPurpose(String agreedPurpose) {
        this.agreedPurpose = agreedPurpose;
    }

    public Integer getAgreedDurationDays() {
        return agreedDurationDays;
    }

    public void setAgreedDurationDays(Integer agreedDurationDays) {
        this.agreedDurationDays = agreedDurationDays;
    }

    public LocalDateTime getAgreedAt() {
        return agreedAt;
    }

    public void setAgreedAt(LocalDateTime agreedAt) {
        this.agreedAt = agreedAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(User decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public LocalDateTime getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(LocalDateTime reservedAt) {
        this.reservedAt = reservedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}