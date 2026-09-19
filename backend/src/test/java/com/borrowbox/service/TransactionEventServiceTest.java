package com.borrowbox.service;

import com.borrowbox.dto.TransactionEventResponse;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.User;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.TransactionEventDeliveryRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * V2.3.1 ledger timeline unit tests: deterministic ordering, participant-only
 * authorization, and null-actor (system) mapping.
 */
@ExtendWith(MockitoExtension.class)
public class TransactionEventServiceTest {

    @Mock
    private TransactionEventRepository eventRepository;

    @Mock
    private TransactionEventDeliveryRepository deliveryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private TransactionEventService eventService;

    private User lender;
    private User borrower;
    private User outsider;
    private Transaction txn;

    @BeforeEach
    void setUp() {
        eventService = new TransactionEventService(
                eventRepository, deliveryRepository, transactionRepository);

        lender = new User("Ahmed", "ahmed@example.com");
        lender.setId(100L);

        borrower = new User("Karim", "karim@example.com");
        borrower.setId(101L);

        outsider = new User("Salah", "salah@example.com");
        outsider.setId(999L);

        txn = new Transaction();
        txn.setId(500L);
        txn.setLender(lender);
        txn.setBorrower(borrower);
    }

    private TransactionEvent event(Long id, TransactionEventType type, User actor, LocalDateTime createdAt) {
        TransactionEvent event = new TransactionEvent();
        event.setId(id);
        event.setTransaction(txn);
        event.setEventType(type);
        event.setActor(actor);
        event.setCreatedAt(createdAt);
        return event;
    }

    @Test
    void borrowerCanViewTimelineInDeterministicOrder() {
        LocalDateTime t1 = LocalDateTime.of(2026, 7, 20, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 7, 20, 10, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 7, 22, 12, 30);
        when(transactionRepository.findById(500L)).thenReturn(Optional.of(txn));
        when(eventRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(500L)).thenReturn(List.of(
                event(1L, TransactionEventType.REQUEST_APPROVED, lender, t1),
                event(2L, TransactionEventType.HANDOVER_SCHEDULED, lender, t2),
                event(3L, TransactionEventType.LOAN_COMPLETED, lender, t3)));

        List<TransactionEventResponse> timeline = eventService.getTimeline(500L, borrower);

        assertThat(timeline).extracting(TransactionEventResponse::eventType)
                .containsExactly(
                        TransactionEventType.REQUEST_APPROVED,
                        TransactionEventType.HANDOVER_SCHEDULED,
                        TransactionEventType.LOAN_COMPLETED);
        assertThat(timeline).extracting(TransactionEventResponse::id)
                .containsExactly(1L, 2L, 3L);
        assertThat(timeline).allSatisfy(r -> assertThat(r.transactionId()).isEqualTo(500L));
        verify(eventRepository).findByTransactionIdOrderByCreatedAtAscIdAsc(500L);
    }

    @Test
    void lenderCanViewTimeline() {
        when(transactionRepository.findById(500L)).thenReturn(Optional.of(txn));
        when(eventRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(500L)).thenReturn(List.of(
                event(1L, TransactionEventType.REQUEST_APPROVED, lender,
                        LocalDateTime.of(2026, 7, 20, 10, 0))));

        List<TransactionEventResponse> timeline = eventService.getTimeline(500L, lender);

        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).actorName()).isEqualTo("Ahmed");
    }

    @Test
    void nullUserIsRejected() {
        assertThatThrownBy(() -> eventService.getTimeline(500L, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authentication required");
        verifyNoInteractions(transactionRepository, eventRepository);
    }

    @Test
    void unknownTransactionThrowsNotFound() {
        when(transactionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getTimeline(404L, borrower))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void outsiderIsRejected() {
        when(transactionRepository.findById(500L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> eventService.getTimeline(500L, outsider))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("participants");
    }

    @Test
    void systemEventWithNullActorMapsToNullActorFields() {
        when(transactionRepository.findById(500L)).thenReturn(Optional.of(txn));
        when(eventRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(500L)).thenReturn(List.of(
                event(9L, TransactionEventType.REQUEST_APPROVED, null,
                        LocalDateTime.of(2026, 7, 20, 10, 0))));

        List<TransactionEventResponse> timeline = eventService.getTimeline(500L, borrower);

        assertThat(timeline.get(0).actorId()).isNull();
        assertThat(timeline.get(0).actorName()).isNull();
        assertThat(timeline.get(0).eventType()).isEqualTo(TransactionEventType.REQUEST_APPROVED);
    }
}
