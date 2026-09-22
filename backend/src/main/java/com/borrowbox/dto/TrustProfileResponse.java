package com.borrowbox.dto;

/**
 * V2.3.1 derived trust/reliability profile for a single user.
 * <p>
 * All metrics are derived on read from the canonical transaction rows; nothing
 * is persisted. When no community scope is requested, communityId and
 * communityName are null and metrics cover every community the user has
 * transacted in.
 *
 * @param communityId           the requested community scope, or null for GLOBAL
 * @param communityName         the requested community name, or null for GLOBAL
 * @param itemsBorrowed         transactions where the user is borrower in a loan state
 * @param itemsLent             transactions where the user is lender in a loan state
 * @param successfulTransactions completed transactions across both roles
 * @param completedLoans        completed borrower transactions with a dueAt
 * @param onTimeReturns         completed loans returned on or before the final dueAt
 * @param lateReturns           completed loans returned after the final dueAt
 * @param onTimeReturnRate      onTimeReturns / completedLoans, or null when completedLoans == 0
 * @param returnDisputes        borrower RETURN_DISPUTED events
 * @param completedLends        completed loans where user acted as lender
 * @param returnDisputesReceived RETURN_DISPUTED events where user was the lender
 */
public record TrustProfileResponse(
        Long communityId,
        String communityName,
        int itemsBorrowed,
        int itemsLent,
        int successfulTransactions,
        int completedLoans,
        int onTimeReturns,
        int lateReturns,
        Double onTimeReturnRate,
        int returnDisputes,
        int completedLends,
        int returnDisputesReceived
) {
}