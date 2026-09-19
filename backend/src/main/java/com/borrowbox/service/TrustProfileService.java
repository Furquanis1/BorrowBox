package com.borrowbox.service;

import com.borrowbox.dto.TrustProfileResponse;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.TransactionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * V2.3.1 derived trust/reliability profile.
 * <p>
 * Every metric is computed on read from the canonical transaction rows; nothing
 * is stored. The only states that count as an actual borrowed/lent item are the
 * explicit loan-state set below. HANDOVER_DISPUTED is deliberately excluded: the
 * handover was disputed before a successful loan, so it is never an actual
 * borrowed/lent item, never successful and never part of the on-time denominator.
 */
@Service
@Transactional(readOnly = true)
public class TrustProfileService {

    private static final Set<TransactionStatus> LOAN_STATES = EnumSet.of(
            TransactionStatus.ACTIVE,
            TransactionStatus.RETURN_INITIATED,
            TransactionStatus.RETURN_REPORTED,
            TransactionStatus.COMPLETED,
            TransactionStatus.RETURN_DISPUTED
    );

    private final TransactionRepository transactionRepository;
    private final MembershipRepository membershipRepository;

    public TrustProfileService(TransactionRepository transactionRepository,
                               MembershipRepository membershipRepository) {
        this.transactionRepository = transactionRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Derives the trust profile for a user. Global when communityId is null,
     * otherwise scoped to one community (the caller must hold ACTIVE
     * membership, else access is denied with 403 semantics).
     */
    public TrustProfileResponse getTrustProfile(Long userId, Long communityId) {
        if (userId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        String communityName = null;
        if (communityId != null) {
            Membership membership = membershipRepository
                    .findByUserIdAndCommunityIdAndStatus(userId, communityId, MembershipStatus.ACTIVE)
                    .orElseThrow(() -> new AccessDeniedException(
                            "You are not an active member of this community"));
            communityName = membership.getCommunity() != null
                    ? membership.getCommunity().getName()
                    : null;
        }

        List<Transaction> borrowed = borrowedTransactions(userId, communityId);
        List<Transaction> lent = lentTransactions(userId, communityId);

        int itemsBorrowed = countLoanStates(borrowed);
        int itemsLent = countLoanStates(lent);

        int successfulTransactions = (int) Stream.concat(borrowed.stream(), lent.stream())
                .filter(t -> t.getState() == TransactionStatus.COMPLETED)
                .map(Transaction::getId)
                .distinct()
                .count();

        List<Transaction> completedLoans = borrowed.stream()
                .filter(t -> t.getState() == TransactionStatus.COMPLETED
                        && t.getDueAt() != null)
                .toList();
        int completedLoansCount = completedLoans.size();
        int onTimeReturns = (int) completedLoans.stream()
                .filter(t -> t.getCompletedAt() != null
                        && !t.getCompletedAt().isAfter(t.getDueAt()))
                .count();
        int lateReturns = completedLoansCount - onTimeReturns;
        Double onTimeReturnRate = completedLoansCount == 0
                ? null
                : (double) onTimeReturns / completedLoansCount;

        return new TrustProfileResponse(communityId, communityName,
                itemsBorrowed, itemsLent, successfulTransactions,
                completedLoansCount, onTimeReturns, lateReturns, onTimeReturnRate);
    }

    private List<Transaction> borrowedTransactions(Long userId, Long communityId) {
        return communityId != null
                ? transactionRepository.findByBorrowerIdAndCommunityIdOrderByIdDesc(userId, communityId)
                : transactionRepository.findByBorrowerIdOrderByIdDesc(userId);
    }

    private List<Transaction> lentTransactions(Long userId, Long communityId) {
        return communityId != null
                ? transactionRepository.findByLenderIdAndCommunityIdOrderByIdDesc(userId, communityId)
                : transactionRepository.findByLenderIdOrderByIdDesc(userId);
    }

    private int countLoanStates(List<Transaction> transactions) {
        return (int) transactions.stream()
                .filter(t -> LOAN_STATES.contains(t.getState()))
                .count();
    }
}