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
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * V2.3.2 durable reputation event — one immutable outcome row in the
 * append-only reputation ledger (ADR-020, ADR-021). Written the instant a
 * transaction reaches a reputation-bearing terminal state, stamped with the
 * authoritative server clock (occurredAt), and never edited or deleted.
 *
 * <p>Two event kinds exist (ADR-020 taxonomy):
 *
 * <ul>
 *   <li>{@code LOAN_COMPLETED}: appended on {@code confirmReturn} as TWO rows
 *       atomically — borrower (role=BORROWER, successful=true, onTime =
 *       completedAt &lt;= final dueAt) and lender (role=LENDER,
 *       successful=true, onTime = null because a lender faces no on-time
 *       deadline).</li>
 *   <li>{@code RETURN_DISPUTED}: appended on {@code disputeReturn} as ONE
 *       borrower row (role=BORROWER, successful=false, onTime=false).</li>
 * </ul>
 *
 * <p>Identity/idempotency: {@code UNIQUE(transaction_id, user_id, event_type)}
 * in schema.sql plus an existence check in the service BEFORE insert, so a
 * replayed transition or re-run self-healing reconcile can never duplicate a
 * row.
 */
@Entity
@Table(name = "reputation_events")
public class ReputationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private ReputationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private ReputationRole role;

    /** nullable: null for lender LOAN_COMPLETED rows, false for RETURN_DISPUTED */
    @Column(name = "on_time")
    private Boolean onTime;

    @Column(name = "successful", nullable = false)
    private boolean successful;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ReputationEvent() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Community getCommunity() {
        return community;
    }

    public void setCommunity(Community community) {
        this.community = community;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public ReputationEventType getEventType() {
        return eventType;
    }

    public void setEventType(ReputationEventType eventType) {
        this.eventType = eventType;
    }

    public ReputationRole getRole() {
        return role;
    }

    public void setRole(ReputationRole role) {
        this.role = role;
    }

    public Boolean getOnTime() {
        return onTime;
    }

    public void setOnTime(Boolean onTime) {
        this.onTime = onTime;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
