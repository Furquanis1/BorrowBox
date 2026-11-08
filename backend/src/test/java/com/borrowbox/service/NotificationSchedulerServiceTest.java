package com.borrowbox.service;

import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.repository.TransactionRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NotificationSchedulerServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private NotificationService notificationService;

    private NotificationSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        schedulerService = new NotificationSchedulerService(transactionRepository, notificationService);
    }

    private Transaction transaction(Long id, User borrower, User lender,
                                    String purpose, LocalDateTime dueAt) {
        Transaction txn = new Transaction();
        txn.setId(id);
        txn.setBorrower(borrower);
        txn.setLender(lender);
        txn.setPurpose(purpose);
        txn.setDueAt(dueAt);
        return txn;
    }

    @Test
    void dueSoonNotifiesBorrowerOnly() {
        User borrower = new User("Borrower", "borrower@example.com");
        borrower.setId(1L);
        User lender = new User("Lender", "lender@example.com");
        lender.setId(2L);
        Transaction dueSoon = transaction(10L, borrower, lender, "Football",
                LocalDateTime.now().plusHours(12));

        when(transactionRepository.findSchedulerDueSoonForUpdate(
                eq(TransactionStatus.ACTIVE), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dueSoon));
        when(transactionRepository.findSchedulerOverdueForUpdate(
                eq(TransactionStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of());

        schedulerService.checkDueSoonAndOverdue();

        verify(notificationService).createScheduled(
                eq(borrower),
                eq(NotificationType.LOAN_DUE_SOON),
                eq(10L),
                eq(dueSoon.getDueAt()),
                eq("Loan due soon"),
                eq("Your loan 'Football' is due in " + dueSoon.getDueAt().toLocalDate() + "."));
    }

    @Test
    void overdueNotifiesBorrowerAndLenderWithDistinctMessages() {
        User borrower = new User("Borrower", "borrower@example.com");
        borrower.setId(1L);
        User lender = new User("Lender", "lender@example.com");
        lender.setId(2L);
        Transaction overdue = transaction(20L, borrower, lender, "Tent",
                LocalDateTime.now().minusDays(1));

        when(transactionRepository.findSchedulerDueSoonForUpdate(
                eq(TransactionStatus.ACTIVE), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(transactionRepository.findSchedulerOverdueForUpdate(
                eq(TransactionStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(overdue));

        schedulerService.checkDueSoonAndOverdue();

        verify(notificationService).createScheduled(
                eq(borrower),
                eq(NotificationType.LOAN_OVERDUE),
                eq(20L),
                eq(overdue.getDueAt()),
                eq("Loan overdue"),
                eq("Your loan 'Tent' is overdue. It was due on "
                        + overdue.getDueAt().toLocalDate() + "."));
        verify(notificationService).createScheduled(
                eq(lender),
                eq(NotificationType.LOAN_OVERDUE),
                eq(20L),
                eq(overdue.getDueAt()),
                eq("Borrower overdue"),
                eq("The borrower's loan 'Tent' is overdue. It was due on "
                        + overdue.getDueAt().toLocalDate() + "."));
    }

    @Test
    void scheduledAnnotationUsesSeconds() throws Exception {
        Method method = NotificationSchedulerService.class
                .getMethod("checkDueSoonAndOverdue");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.SECONDS);
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${borrowbox.notification.check-interval-seconds:300}");
    }

    @Test
    void schedulerRepositoryQueriesLockAndOrderCandidates() throws Exception {
        Method dueSoon = TransactionRepository.class.getMethod(
                "findSchedulerDueSoonForUpdate",
                TransactionStatus.class, LocalDateTime.class, LocalDateTime.class);
        Method overdue = TransactionRepository.class.getMethod(
                "findSchedulerOverdueForUpdate",
                TransactionStatus.class, LocalDateTime.class);

        assertThat(dueSoon.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(overdue.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(dueSoon.getAnnotation(Query.class).value())
                .contains("order by t.dueAt asc, t.id asc");
        assertThat(overdue.getAnnotation(Query.class).value())
                .contains("order by t.dueAt asc, t.id asc");
    }
}
