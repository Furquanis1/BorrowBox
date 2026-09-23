package com.borrowbox.dto;

/**
 * V2.4.2 community health directory card. The health summary is the same
 * aggregation the dashboard uses; rates are null when the denominator is zero.
 */
public record HealthResponse(
        Long communityId,
        int activeMemberCount,
        int openFlagCount,
        int overdueLoanCount,
        int completedLoansCount,
        int onTimeReturns,
        Integer onTimeReturnRate,
        int returnDisputesCount,
        int completedLendsCount,
        int returnDisputesReceivedCount,
        Integer disputeRate
) {
}