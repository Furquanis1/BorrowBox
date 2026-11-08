package com.borrowbox.service;

import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.User;
import com.borrowbox.repository.TransactionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * V2.6: Scheduled service for due-soon and overdue notification automation.
 *
 * Runs periodically to check for transactions that are due soon (within 24
 * hours) or overdue (past due date), and sends notifications to the borrower
 * (and lender for overdue) accordingly.
 *
 * Duplicate prevention: each scheduled reminder type + transaction combination
 * is created at most once, enforced by the UNIQUE(recipient_id, dedupe_key)
 * constraint on the notifications table and the service's atomic insert.
 */
@Service
@ConditionalOnProperty(name = "borrowbox.notification.scheduler.enabled",
        havingValue = "true", matchIfMissing = true)
public class NotificationSchedulerService {

    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    public NotificationSchedulerService(TransactionRepository transactionRepository,
                                        NotificationService notificationService) {
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
    }

    /**
     * Runs every 5 minutes to check for due-soon and overdue loans.
     * Notifications are sent only once per transaction, type, and due date.
     */
    @Scheduled(fixedDelayString = "${borrowbox.notification.check-interval-seconds:300}",
            timeUnit = TimeUnit.SECONDS)
    @Transactional
    public void checkDueSoonAndOverdue() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueSoonThreshold = now.plusHours(24);

        List<Transaction> dueSoon = transactionRepository
                .findSchedulerDueSoonForUpdate(
                        com.borrowbox.entity.TransactionStatus.ACTIVE,
                        now, dueSoonThreshold);

        for (Transaction txn : dueSoon) {
            notify(txn.getBorrower(),
                    NotificationType.LOAN_DUE_SOON,
                    txn.getId(),
                    txn.getDueAt(),
                    "Loan due soon",
                    "Your loan '" + txn.getPurpose() + "' is due in " +
                            txn.getDueAt().toLocalDate() + ".");
        }

        List<Transaction> overdue = transactionRepository
                .findSchedulerOverdueForUpdate(
                        com.borrowbox.entity.TransactionStatus.ACTIVE, now);

        for (Transaction txn : overdue) {
            notify(txn.getBorrower(),
                    NotificationType.LOAN_OVERDUE,
                    txn.getId(),
                    txn.getDueAt(),
                    "Loan overdue",
                    "Your loan '" + txn.getPurpose() + "' is overdue. " +
                            "It was due on " + txn.getDueAt().toLocalDate() + ".");

            notify(txn.getLender(),
                    NotificationType.LOAN_OVERDUE,
                    txn.getId(),
                    txn.getDueAt(),
                    "Borrower overdue",
                    "The borrower's loan '" + txn.getPurpose() + "' is overdue. " +
                            "It was due on " + txn.getDueAt().toLocalDate() + ".");
        }
    }

    private void notify(User recipient, NotificationType type,
                        Long transactionId, LocalDateTime dueAt,
                        String title, String message) {
        if (recipient == null) {
            return;
        }
        notificationService.createScheduled(
                recipient, type, transactionId, dueAt, title, message);
    }
}
