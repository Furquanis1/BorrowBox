package com.borrowbox.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One recorded change of the asset_units.condition current-state projection
 * (V2.5.3).
 *
 * Evidence stays the immutable source record of a condition observation; this
 * table only records what the projection became, when, and by which lifecycle
 * actor, so a unit's condition can be read as of any point in its past.
 *
 * Append-only by construction: rows are inserted inside the same lifecycle
 * transaction that moves the projection (confirmHandover, confirmReturn) and no
 * update or delete path exists in the application. previous_condition is NULL
 * for the first recorded observation of a unit, new_condition is never NULL and
 * never carries a description that was not present on the evidence row.
 */
@Entity
@Table(name = "asset_unit_condition_history")
public class AssetUnitConditionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_unit_id", nullable = false)
    private AssetUnit assetUnit;

    @Column(name = "previous_condition", length = 1000)
    private String previousCondition;

    @Column(name = "new_condition", nullable = false, length = 1000)
    private String newCondition;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id")
    private Evidence evidence;

    public AssetUnitConditionHistory() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AssetUnit getAssetUnit() {
        return assetUnit;
    }

    public void setAssetUnit(AssetUnit assetUnit) {
        this.assetUnit = assetUnit;
    }

    public String getPreviousCondition() {
        return previousCondition;
    }

    public void setPreviousCondition(String previousCondition) {
        this.previousCondition = previousCondition;
    }

    public String getNewCondition() {
        return newCondition;
    }

    public void setNewCondition(String newCondition) {
        this.newCondition = newCondition;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public void setEvidence(Evidence evidence) {
        this.evidence = evidence;
    }
}
