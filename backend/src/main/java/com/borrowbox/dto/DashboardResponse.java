package com.borrowbox.dto;

import java.util.List;

/**
 * V2.4.2 community manager dashboard roll-up. All counts are scoped to one
 * community; the on-time return rate and dispute rate are null when the
 * denominator is zero.
 */
public record DashboardResponse(
        Long communityId,
        int activeLoanCount,
        int overdueLoanCount,
        int pendingMembershipCount,
        int activeMemberCount,
        int openFlagCount,
        int completedLoansCount,
        int onTimeReturns,
        int lateReturns,
        Integer onTimeReturnRate,
        int returnDisputesCount,
        int completedLendsCount,
        int returnDisputesReceivedCount,
        Integer disputeRate,
        int transactionVolume30d,
        List<RecentActivityDto> recentActivity,
        List<FlagResponse> recentFlags
) {
}