package com.borrowbox.service;

import com.borrowbox.dto.TransactionMessageResponse;
import com.borrowbox.entity.MessageKind;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionMessage;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.TransactionMessageRepository;
import com.borrowbox.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * V2.2.3 transaction conversation.
 *
 * Messaging is orthogonal to the transaction state machine: it never mutates
 * transaction state, AssetUnit state, reservations or availability. It only
 * creates rows in transaction_messages.
 *
 * Write window is the borrowed-life coordination phases
 * (APPROVED / AWAITING_HANDOVER / ACTIVE / RETURN_INITIATED / RETURN_REPORTED).
 * Terminal transactions (COMPLETED / REJECTED / CANCELLED) remain readable as
 * an archive. SYSTEM events are emitted by TransactionService lifecycle
 * transitions inside the same backend transaction.
 */
@Service
public class TransactionMessageService {

    public static final int MAX_MESSAGE_LENGTH = 1000;

    private final TransactionMessageRepository messageRepository;
    private final TransactionRepository transactionRepository;
    private final MembershipService membershipService;

    public TransactionMessageService(TransactionMessageRepository messageRepository,
                                     TransactionRepository transactionRepository,
                                     MembershipService membershipService) {
        this.messageRepository = messageRepository;
        this.transactionRepository = transactionRepository;
        this.membershipService = membershipService;
    }

    /**
     * Participant sends a USER message.
     *
     * Sends nothing else: no state, no AssetUnit, no reservation changes.
     */
    @Transactional
    public TransactionMessageResponse sendMessage(Long transactionId, User actor, String body) {
        requireUser(actor);
        Transaction txn = findOrThrow(transactionId);
        requireParticipant(txn, actor);
        requireWritable(txn);
        requireActiveMember(actor.getId(), txn.getCommunity().getId());

        String normalized = trimmed(body);
        if (normalized.isBlank()) {
            throw new BusinessRuleViolationException("A message cannot be empty");
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessRuleViolationException(
                    "A message must be at most " + MAX_MESSAGE_LENGTH + " characters");
        }

        TransactionMessage message = new TransactionMessage();
        message.setTransaction(txn);
        message.setAuthor(actor);
        message.setKind(MessageKind.USER);
        message.setBody(normalized);
        return toResponse(messageRepository.save(message));
    }

    /**
     * Participants read the full conversation, regardless of transaction state.
     */
    @Transactional(readOnly = true)
    public List<TransactionMessageResponse> listMessages(Long transactionId, User actor) {
        requireUser(actor);
        Transaction txn = findOrThrow(transactionId);
        requireParticipant(txn, actor);
        return messageRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Server-only timeline entry. Called by TransactionService transitions so
     * the state change and its SYSTEM message commit atomically. Not reachable
     * through any public API and never created by participants.
     */
    @Transactional
    public TransactionMessageResponse addSystemEvent(Transaction txn, String body) {
        TransactionMessage message = new TransactionMessage();
        message.setTransaction(txn);
        message.setAuthor(null);
        message.setKind(MessageKind.SYSTEM);
        message.setBody(body);
        return toResponse(messageRepository.save(message));
    }

    private Transaction findOrThrow(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found with id: " + transactionId));
    }

    private void requireWritable(Transaction txn) {
        TransactionStatus state = txn.getState();
        boolean writable = switch (state) {
            case APPROVED, AWAITING_HANDOVER, ACTIVE, RETURN_INITIATED, RETURN_REPORTED -> true;
            default -> false;
        };
        if (!writable) {
            throw new BusinessRuleViolationException(
                    "A conversation message cannot be sent while the transaction is " + state);
        }
    }

    private void requireUser(User actor) {
        if (actor == null) {
            throw new UnauthorizedException("Authentication is required");
        }
    }

    private void requireParticipant(Transaction txn, User actor) {
        if (!txn.getBorrower().getId().equals(actor.getId())
                && !txn.getLender().getId().equals(actor.getId())) {
            throw new UnauthorizedException("Only participants can view this conversation");
        }
    }

    private void requireActiveMember(Long userId, Long communityId) {
        if (!membershipService.isActiveMember(userId, communityId)) {
            throw new UnauthorizedException(
                    "Only active members of this community can use its transactions");
        }
    }

    private String trimmed(String body) {
        return body == null ? "" : body.trim();
    }

    private TransactionMessageResponse toResponse(TransactionMessage message) {
        User author = message.getAuthor();
        return new TransactionMessageResponse(
                message.getId(),
                message.getTransaction().getId(),
                author == null ? null : author.getId(),
                author == null ? null : author.getFullName(),
                message.getKind(),
                message.getBody(),
                message.getCreatedAt()
        );
    }
}