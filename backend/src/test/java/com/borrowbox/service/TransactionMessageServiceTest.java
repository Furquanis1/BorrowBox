package com.borrowbox.service;

import com.borrowbox.dto.TransactionMessageResponse;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.AssetUnit;
import com.borrowbox.entity.AssetUnitStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.ListingStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionMessageServiceTest {

    @Mock
    private TransactionMessageRepository messageRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MembershipService membershipService;

    private TransactionMessageService messageService;

    private User owner;
    private User borrower;
    private User intruder;
    private Transaction txn;
    private AssetUnit unit;

    @BeforeEach
    void setUp() {
        messageService = new TransactionMessageService(
                messageRepository, transactionRepository, membershipService);

        owner = new User("Ahmed", "ahmed@example.com");
        owner.setId(100L);

        borrower = new User("Salah", "salah@example.com");
        borrower.setId(101L);

        intruder = new User("Karim", "karim@example.com");
        intruder.setId(999L);

        unit = new AssetUnit();
        unit.setId(777L);
        unit.setStatus(AssetUnitStatus.RESERVED);

        Asset football = new Asset();
        football.setId(500L);
        football.setOwner(owner);
        football.setTitle("Football");
        football.setStatus(AssetStatus.ACTIVE);

        Community cse = new Community();
        cse.setId(900L);
        cse.setName("CSE Department");

        CommunityListing listing = new CommunityListing();
        listing.setId(701L);
        listing.setAsset(football);
        listing.setCommunity(cse);
        listing.setListingStatus(ListingStatus.LISTED);

        txn = new Transaction();
        txn.setId(1L);
        txn.setCommunity(cse);
        txn.setListing(listing);
        txn.setAsset(football);
        txn.setBorrower(borrower);
        txn.setLender(owner);
        txn.setReservedUnit(unit);
        txn.setReservedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        txn.setState(TransactionStatus.APPROVED);
        txn.setPurpose("Football match practice");
        txn.setRequestedDurationDays(3);
    }

    private void stubApprovedTxnForWrite() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(anyLong(), eq(900L))).thenReturn(true);
        when(messageRepository.save(any(TransactionMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void intoState(TransactionStatus state) {
        txn.setState(state);
    }

    // ── sendMessage ──────────────────────────────────────────────────

    @Test
    void anyParticipantCanSend() {
        stubApprovedTxnForWrite();

        TransactionMessageResponse fromBorrower = messageService.sendMessage(1L, borrower, "Friday at 5pm at reception");

        assertThat(fromBorrower.kind()).isEqualTo(MessageKind.USER);
        assertThat(fromBorrower.authorId()).isEqualTo(101L);
        assertThat(fromBorrower.authorName()).isEqualTo("Salah");
        assertThat(fromBorrower.body()).isEqualTo("Friday at 5pm at reception");
        assertThat(fromBorrower.transactionId()).isEqualTo(1L);

        TransactionMessageResponse fromLender = messageService.sendMessage(1L, owner, "Sounds good");

        assertThat(fromLender.authorId()).isEqualTo(100L);
        assertThat(fromLender.authorName()).isEqualTo("Ahmed");
        assertThat(fromLender.kind()).isEqualTo(MessageKind.USER);
    }

    @Test
    void sendTrimsSurroundingWhitespace() {
        stubApprovedTxnForWrite();

        TransactionMessageResponse response = messageService.sendMessage(1L, borrower, "  See you at the hostel gate  ");

        assertThat(response.body()).isEqualTo("See you at the hostel gate");
    }

    @Test
    void blankMessageIsRejected() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(anyLong(), eq(900L))).thenReturn(true);

        assertThatThrownBy(() -> messageService.sendMessage(1L, borrower, "   "))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("A message cannot be empty");
        assertThatThrownBy(() -> messageService.sendMessage(1L, borrower, ""))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> messageService.sendMessage(1L, borrower, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void overLongMessageIsRejected() {
        stubApprovedTxnForWrite();

        assertThatThrownBy(() -> messageService.sendMessage(1L, borrower, "x".repeat(1001)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("A message must be at most 1000 characters");

        // Exactly at the limit is accepted after trimming.
        TransactionMessageResponse response = messageService.sendMessage(1L, borrower, "x".repeat(1000));
        assertThat(response.body()).hasSize(1000);
    }

    @Test
    void outsiderCannotWrite() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> messageService.sendMessage(1L, intruder, "Can I? No."))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void outsiderCannotRead() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> messageService.listMessages(1L, intruder))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void writeRequiresActiveMembership() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));
        when(membershipService.isActiveMember(eq(100L), eq(900L))).thenReturn(false);

        assertThatThrownBy(() -> messageService.sendMessage(1L, owner, "hello"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void writeWindowRejectsNonWritableStates() {
        for (TransactionStatus state : List.of(
                TransactionStatus.PENDING,
                TransactionStatus.COUNTER_OFFERED,
                TransactionStatus.COMPLETED,
                TransactionStatus.REJECTED,
                TransactionStatus.CANCELLED)) {
            intoState(state);
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

            assertThatThrownBy(() -> messageService.sendMessage(1L, borrower, "message"))
                    .as("write rejected for %s", state)
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Test
    void writeAllowedWhileReturnReported() {
        intoState(TransactionStatus.RETURN_REPORTED);
        stubApprovedTxnForWrite();

        TransactionMessageResponse response = messageService.sendMessage(1L, borrower, "It is back with Ahmed");

        assertThat(response.kind()).isEqualTo(MessageKind.USER);
        assertThat(response.authorId()).isEqualTo(101L);
        assertThat(response.body()).isEqualTo("It is back with Ahmed");
    }

    @Test
    void terminalTransactionsAllowReads() {
        intoState(TransactionStatus.COMPLETED);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));
        when(messageRepository.findByTransactionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        assertThat(messageService.listMessages(1L, borrower)).isEmpty();
        assertThat(messageService.listMessages(1L, owner)).isEmpty();
    }

    @Test
    void missingTransactionForWriteIsNotFound() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(1L, borrower, "hello"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void missingTransactionForReadIsNotFound() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.listMessages(1L, borrower))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unauthenticatedActorIsRejected() {
        assertThatThrownBy(() -> messageService.sendMessage(1L, null, "hello"))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> messageService.listMessages(1L, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void sendAlwaysCreatesUserMessage() {
        stubApprovedTxnForWrite();

        messageService.sendMessage(1L, borrower, "Pickup at the gate");

        verify(messageRepository).save(any(TransactionMessage.class));
        org.mockito.ArgumentCaptor<TransactionMessage> captor =
                org.mockito.ArgumentCaptor.forClass(TransactionMessage.class);
        verify(messageRepository).save(captor.capture());
        TransactionMessage saved = captor.getValue();
        // SYSTEM rows can never be produced through the public send path.
        assertThat(saved.getKind()).isEqualTo(MessageKind.USER);
        assertThat(saved.getAuthor()).isNotNull();
        assertThat(saved.getTransaction().getId()).isEqualTo(1L);
    }

    // ── listMessages ─────────────────────────────────────────────────

    @Test
    void listReturnsChronologicalOrder() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(txn));

        TransactionMessage systemMessage = new TransactionMessage();
        systemMessage.setId(11L);
        systemMessage.setTransaction(txn);
        systemMessage.setAuthor(null);
        systemMessage.setKind(MessageKind.SYSTEM);
        systemMessage.setBody("Handover scheduled");

        TransactionMessage userMessage = new TransactionMessage();
        userMessage.setId(12L);
        userMessage.setTransaction(txn);
        userMessage.setAuthor(borrower);
        userMessage.setKind(MessageKind.USER);
        userMessage.setBody("See you soon");

        when(messageRepository.findByTransactionIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(systemMessage, userMessage));

        List<TransactionMessageResponse> response = messageService.listMessages(1L, owner);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).kind()).isEqualTo(MessageKind.SYSTEM);
        assertThat(response.get(0).authorId()).isNull();
        assertThat(response.get(0).authorName()).isNull();
        assertThat(response.get(0).body()).isEqualTo("Handover scheduled");
        assertThat(response.get(1).kind()).isEqualTo(MessageKind.USER);
        assertThat(response.get(1).authorName()).isEqualTo("Salah");
        verify(messageRepository).findByTransactionIdOrderByCreatedAtAsc(1L);
    }

    // ── addSystemEvent (internal) ────────────────────────────────────

    @Test
    void addSystemEventWritesNullAuthorSystemRow() {
        when(messageRepository.save(any(TransactionMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransactionMessageResponse response = messageService.addSystemEvent(txn, "Loan started");

        assertThat(response.kind()).isEqualTo(MessageKind.SYSTEM);
        assertThat(response.authorId()).isNull();
        assertThat(response.authorName()).isNull();
        assertThat(response.body()).isEqualTo("Loan started");
        assertThat(response.transactionId()).isEqualTo(1L);
    }

    // ── orthogonality safeguards ─────────────────────────────────────

    @Test
    void messagingDoesNotMutateTransactionState() {
        stubApprovedTxnForWrite();

        messageService.sendMessage(1L, borrower, "keeping the booking");

        assertThat(txn.getState()).isEqualTo(TransactionStatus.APPROVED);
    }

    @Test
    void messagingDoesNotMutateReservationOrUnitState() {
        stubApprovedTxnForWrite();
        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.RESERVED);

        messageService.sendMessage(1L, borrower, "still reserved for the match");

        assertThat(unit.getStatus()).isEqualTo(AssetUnitStatus.RESERVED);
        assertThat(txn.getReservedUnit()).isSameAs(unit);
    }
}