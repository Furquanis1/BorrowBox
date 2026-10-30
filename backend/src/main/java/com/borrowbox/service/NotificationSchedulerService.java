package com.borrowbox.service;

import com.borrowbox.entity.Notification;
import com.borrowbox.entity.NotificationType;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.User;
import com.borrowbox.repository.NotificationPreferenceRepository;
import com.borrowbox.repository.NotificationRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V2.6: Scheduled service for due-soon and overdue notification automation.
 *
 * Runs periodically to check for transactions that are due soon (within 24
 * hours) or overdue (past due date), and sends notifications to the borrower
 * (and lender for overdue) accordingly.
 *
 * Duplicate prevention: each notification type + transaction combination is
 * created at most once, enforced by the UNIQUE(user_id, type, transaction_id)
 * constraint on the notifications table plus an existence check before insert.
 */
@Service
public class NotificationSchedulerService {

    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public NotificationSchedulerService(TransactionRepository transactionRepository,
                                        NotificationService notificationService,
                                        NotificationPreferenceRepository preferenceRepository,
                                        UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
    }

    /**
     * Runs every 5 minutes to check for due-soon and overdue loans.
     * Notifications are sent only once per transaction per type.
     */
    @Scheduled(fixedDelayString = "${borrowbox.notification.check-interval-seconds:300}")
    @Transactional(readOnly = true)
    public void checkDueSoonAndOverdue() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueSoonThreshold = now.plusHours(24);

        // Find ACTIVE transactions due within 24 hours (due-soon)
        List<Transaction> dueSoon = transactionRepository
                .findByStateAndDueAtBetween(
                        com.borrowbox.entity.TransactionStatus.ACTIVE,
                        now, dueSoonThreshold);

        for (Transaction txn : dueSoon) {
            notifyIfNotExists(txn.getBorrower(),
                    NotificationType.LOAN_DUE_SOON,
                    txn.getId(),
                    "Loan due soon",
                    "Your loan '" + txn.getPurpose() + "' is due in " +
                            txn.getDueAt().toLocalDate() + ".");
        }

        // Find ACTIVE transactions past due (overdue)
        List<Transaction> overdue = transactionRepository
                .findByStateAndDueAtBefore(
                        com.borrowbox.entity.TransactionStatus.ACTIVE, now);

        for (Transaction txn : overdue) {
            // Notify borrower
            notifyIfNotExists(txn.getBorrower(),
                    NotificationType.LOAN_OVERDUE,
                    txn.getId(),
                    "Loan overdue",
                    "Your loan '" + txn.getPurpose() + "' is overdue. " +
                            "It was due on " + txn.getDueAt().toLocalDate() + ".");

            // Notify lender
            notifyIfNotExists(txn.getLender(),
                    NotificationType.LOAN_OVERDUE,
                    txn.getId(),
                    "Borrower overdue",
                    "The borrower's loan '" + txn.getPurpose() + "' is overdue. " +
                            "It was due on " + txn.getDueAt().toLocalDate() + ".");
        }
    }

    /**
     * Creates a notification only if one doesn't already exist for this
     * user + type + transaction combination. Respects user preferences
     * (skips if user has opted out of this type).
     */
    private void notifyIfNotExists(User recipient, NotificationType type,
                                   Long transactionId, String title, String message) {
        if (recipient == null) {
            return;
        }

        // Check if user has opted out of this notification type
        if (preferenceRepository.findByUserIdAndType(recipient.getId(), type)
                .map(p -> !p.isEnabled())
                .orElse(false)) {
            return;
        }

        // Check if notification already exists (idempotency)
        if (notificationService.notificationExists(recipient, type, transactionId)) {
            return;
        }

        notificationService.create(recipient, type, title, message, transactionId);
    }
}
