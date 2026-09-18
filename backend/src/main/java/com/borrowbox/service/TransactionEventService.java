package com.borrowbox.service;

import com.borrowbox.dto.TransactionEventDeliveryResponse;
import com.borrowbox.dto.TransactionEventResponse;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventDelivery;
import com.borrowbox.entity.TransactionEventDeliveryStatus;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.TransactionEventDeliveryRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * V2.2.8 transaction event service.
 *
 * Event creation and delivery creation occur in the SAME transaction as the
 * underlying transaction state mutation (propagation MANDATORY).
 * No REQUIRES_NEW — atomicity with the state change is required.
 */
@Service
public class TransactionEventService {

    private final TransactionEventRepository eventRepository;
    private final TransactionEventDeliveryRepository deliveryRepository;
    private final TransactionRepository transactionRepository;

    public TransactionEventService(TransactionEventRepository eventRepository,
                                   TransactionEventDeliveryRepository deliveryRepository,
                                   TransactionRepository transactionRepository) {
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Creates a transaction event and its per-recipient deliveries.
     * Must be called within the same transaction as the state mutation.
     *
     * @param transaction the transaction the event belongs to
     * @param eventType   the semantic event type
     * @param actor       the user who triggered the transition (null for system events like waitlist promotion)
     * @param payload     optional JSON payload for structured event data
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createEventAndDeliveries(Transaction transaction,
                                         TransactionEventType eventType,
                                         User actor,
                                         String payload) {
        List<User> recipients = resolveRecipients(transaction, eventType);
        if (recipients.isEmpty()) {
            return;
        }

        TransactionEvent event = new TransactionEvent();
        event.setTransaction(transaction);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setPayload(payload);
        TransactionEvent savedEvent = eventRepository.saveAndFlush(event);

        for (User recipient : recipients) {
            TransactionEventDelivery delivery = new TransactionEventDelivery();
            delivery.setEvent(savedEvent);
            delivery.setRecipient(recipient);
            delivery.setStatus(TransactionEventDeliveryStatus.UNREAD);
            deliveryRepository.save(delivery);
        }
    }

    /**
     * Centralized, deterministic recipient resolution.
     * Only transaction participants (borrower/lender) receive events.
     */
    private List<User> resolveRecipients(Transaction transaction, TransactionEventType eventType) {
        User borrower = transaction.getBorrower();
        User lender = transaction.getLender();

        return switch (eventType) {
            case REQUEST_APPROVED, REQUEST_REJECTED -> List.of(borrower);
            case REQUEST_CANCELLED -> List.of(lender);
            case HANDOVER_SCHEDULED, LOAN_STARTED, LOAN_COMPLETED -> List.of(borrower, lender);
            case HANDOVER_CONFIRMED, HANDOVER_DISPUTED -> List.of(lender);
            case EXTENSION_REQUESTED, EXTENSION_COUNTERED -> List.of(lender);
            case EXTENSION_APPROVED, EXTENSION_REJECTED, EXTENSION_COUNTER_ACCEPTED, EXTENSION_COUNTER_REJECTED -> List.of(borrower);
            case RETURN_INITIATED, RETURN_REPORTED -> List.of(lender);
            case RETURN_DISPUTED -> List.of(borrower);
            case WAITLIST_PROMOTED -> List.of(borrower); // promoted waiter is the borrower of the new transaction
            default -> List.of();
        };
    }

    /**
     * Get event deliveries for the current user (borrower or lender).
     * Supports filtering by status.
     */
    @Transactional(readOnly = true)
    public List<TransactionEventDeliveryResponse> getMine(User user, List<TransactionEventDeliveryStatus> statuses) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        List<TransactionEventDelivery> deliveries = deliveryRepository
                .findByRecipientIdAndStatusInOrderByCreatedAtDesc(user.getId(), statuses);
        return deliveries.stream()
                .map(this::toDeliveryResponse)
                .collect(toList());
    }

    /**
     * Get event deliveries for a specific transaction (participants only).
     */
    @Transactional(readOnly = true)
    public List<TransactionEventDeliveryResponse> getByTransaction(Long transactionId, User user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        if (!transaction.getBorrower().getId().equals(user.getId())
                && !transaction.getLender().getId().equals(user.getId())) {
            throw new UnauthorizedException("Only participants can view transaction events");
        }
        List<TransactionEventDelivery> deliveries = deliveryRepository
                .findByRecipientIdAndStatusInOrderByCreatedAtDesc(user.getId(), List.of(
                        TransactionEventDeliveryStatus.UNREAD,
                        TransactionEventDeliveryStatus.READ,
                        TransactionEventDeliveryStatus.DISMISSED
                ));
        // Filter to only this transaction's events
        return deliveries.stream()
                .filter(d -> d.getEvent().getTransaction().getId().equals(transactionId))
                .map(this::toDeliveryResponse)
                .collect(toList());
    }

    /**
     * Mark a delivery as READ (idempotent).
     */
    @Transactional
    public TransactionEventDeliveryResponse markRead(Long deliveryId, User user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        TransactionEventDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + deliveryId));
        if (!delivery.getRecipient().getId().equals(user.getId())) {
            throw new UnauthorizedException("Cannot mark another user's delivery");
        }
        if (delivery.getStatus() == TransactionEventDeliveryStatus.UNREAD) {
            delivery.setStatus(TransactionEventDeliveryStatus.READ);
            delivery.setReadAt(LocalDateTime.now());
            deliveryRepository.save(delivery);
        }
        // If already READ or DISMISSED -> READ transition is allowed per spec
        else if (delivery.getStatus() == TransactionEventDeliveryStatus.DISMISSED) {
            delivery.setStatus(TransactionEventDeliveryStatus.READ);
            delivery.setReadAt(LocalDateTime.now());
            delivery.setDismissedAt(null);
            deliveryRepository.save(delivery);
        }
        return toDeliveryResponse(delivery);
    }

    /**
     * Mark a delivery as DISMISSED (idempotent).
     */
    @Transactional
    public TransactionEventDeliveryResponse dismiss(Long deliveryId, User user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        TransactionEventDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + deliveryId));
        if (!delivery.getRecipient().getId().equals(user.getId())) {
            throw new UnauthorizedException("Cannot dismiss another user's delivery");
        }
        if (delivery.getStatus() != TransactionEventDeliveryStatus.DISMISSED) {
            delivery.setStatus(TransactionEventDeliveryStatus.DISMISSED);
            delivery.setDismissedAt(LocalDateTime.now());
            deliveryRepository.save(delivery);
        }
        return toDeliveryResponse(delivery);
    }

    /**
     * Mark all UNREAD deliveries for the user as READ.
     */
    @Transactional
    public void markAllRead(User user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        List<TransactionEventDelivery> unread = deliveryRepository
                .findByRecipientIdAndStatusOrderByCreatedAtDesc(user.getId(), TransactionEventDeliveryStatus.UNREAD);
        LocalDateTime now = LocalDateTime.now();
        for (TransactionEventDelivery delivery : unread) {
            delivery.setStatus(TransactionEventDeliveryStatus.READ);
            delivery.setReadAt(now);
        }
        deliveryRepository.saveAll(unread);
    }

    private TransactionEventDeliveryResponse toDeliveryResponse(TransactionEventDelivery delivery) {
        TransactionEvent event = delivery.getEvent();
        Transaction transaction = event.getTransaction();
        User actor = event.getActor();
        return new TransactionEventDeliveryResponse(
                delivery.getId(),
                event.getId(),
                event.getEventType(),
                delivery.getStatus(),
                transaction.getId(),
                transaction.getAsset().getId(),
                transaction.getAsset().getTitle(),
                transaction.getCommunity().getId(),
                transaction.getCommunity().getName(),
                actor != null ? actor.getId() : null,
                actor != null ? actor.getFullName() : null,
                event.getPayload(),
                event.getCreatedAt(),
                delivery.getReadAt(),
                delivery.getDismissedAt()
        );
    }
}