package com.borrowbox.service;

import com.borrowbox.dto.DashboardResponse;
import com.borrowbox.dto.FlagResponse;
import com.borrowbox.dto.HealthResponse;
import com.borrowbox.dto.RecentActivityDto;
import com.borrowbox.entity.Flag;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.ReputationEvent;
import com.borrowbox.entity.ReputationEventType;
import com.borrowbox.entity.ReputationRole;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.FlagRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.ReputationEventRepository;
import com.borrowbox.repository.TransactionEventRepository;
import com.borrowbox.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * V2.4.2 community manager dashboard + health. One aggregation is the single
 * source of truth for both endpoints; the health card is a strict subset of
 * the dashboard roll-up. All reads are gated behind an active community
 * manager. Overdue reuses the authoritative loan clock from
 * {@link TransactionService#isOverdue}.
 */
@Service
@Transactional(readOnly = true)
public class CommunityDashboardService {

    public static final int RECENT_ACTIVITY_LIMIT = 20;

    private static final List<TransactionStatus> ACTIVE_LOAN_STATES =
            List.of(TransactionStatus.ACTIVE,
                    TransactionStatus.RETURN_INITIATED,
                    TransactionStatus.RETURN_REPORTED);

    private static final List<TransactionStatus> DISPUTE_STATES =
            List.of(TransactionStatus.HANDOVER_DISPUTED,
                    TransactionStatus.RETURN_DISPUTED);

    private final MembershipService membershipService;
    private final MembershipRepository membershipRepository;
    private final FlagRepository flagRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final TransactionEventRepository transactionEventRepository;
    private final ReputationEventRepository reputationEventRepository;

    public CommunityDashboardService(MembershipService membershipService,
                                     MembershipRepository membershipRepository,
                                     FlagRepository flagRepository,
                                     TransactionRepository transactionRepository,
                                     TransactionService transactionService,
                                     TransactionEventRepository transactionEventRepository,
                                     ReputationEventRepository reputationEventRepository) {
        this.membershipService = membershipService;
        this.membershipRepository = membershipRepository;
        this.flagRepository = flagRepository;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.transactionEventRepository = transactionEventRepository;
        this.reputationEventRepository = reputationEventRepository;
    }

    public DashboardResponse getDashboard(Long communityId, User manager) {
        requireManager(communityId, manager);
        Aggregates aggregates = aggregate(communityId);
        return new DashboardResponse(
                communityId,
                aggregates.activeLoans,
                aggregates.overdue,
                aggregates.pendingMembers,
                aggregates.activeMembers,
                aggregates.openFlags,
                aggregates.completedLoans,
                aggregates.onTime,
                aggregates.completedLoans - aggregates.onTime,
                rate(aggregates.onTime, aggregates.completedLoans),
                aggregates.returnDisputes,
                aggregates.completedLends,
                aggregates.returnDisputesReceived,
                rate(aggregates.disputedLoans, aggregates.completedLoans),
                aggregates.volume30d,
                recentActivity(communityId),
                recentFlags(communityId)
        );
    }

    public HealthResponse getHealth(Long communityId, User manager) {
        requireManager(communityId, manager);
        Aggregates aggregates = aggregate(communityId);
        return new HealthResponse(
                communityId,
                aggregates.activeMembers,
                aggregates.openFlags,
                aggregates.overdue,
                aggregates.completedLoans,
                aggregates.onTime,
                rate(aggregates.onTime, aggregates.completedLoans),
                aggregates.returnDisputes,
                aggregates.completedLends,
                aggregates.returnDisputesReceived,
                rate(aggregates.disputedLoans, aggregates.completedLoans)
        );
    }

    private Aggregates aggregate(Long communityId) {
        int activeLoans = (int) transactionRepository.countByCommunityIdAndStateIn(
                communityId, ACTIVE_LOAN_STATES);
        int overdue = (int) transactionRepository
                .findByCommunityIdAndState(communityId, TransactionStatus.ACTIVE).stream()
                .filter(transactionService::isOverdue)
                .count();
        int disputedLoans = (int) transactionRepository
                .countByCommunityIdAndStateIn(communityId, DISPUTE_STATES);
        int pendingMembers = (int) membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.PENDING);
        int activeMembers = (int) membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);
        int openFlags = (int) flagRepository
                .countByCommunityIdAndStatus(communityId, FlagStatus.OPEN);
        int completedLoans = (int) transactionRepository
                .countByCommunityIdAndStateAndDueAtIsNotNull(communityId, TransactionStatus.COMPLETED);
        int onTime = (int) transactionRepository
                .countCompletedOnTime(communityId, TransactionStatus.COMPLETED);
        int returnDisputes = (int) reputationEventRepository
                .countByCommunityIdAndEventTypeAndRole(
                        communityId, ReputationEventType.RETURN_DISPUTED, ReputationRole.BORROWER);
        int completedLends = (int) reputationEventRepository
                .countByCommunityIdAndEventTypeAndRole(
                        communityId, ReputationEventType.LOAN_COMPLETED, ReputationRole.LENDER);
        int returnDisputesReceived = (int) reputationEventRepository
                .countByCommunityIdAndEventTypeWhereTransactionLenderIsMember(
                        communityId, ReputationEventType.RETURN_DISPUTED);
        int volume30d = (int) transactionRepository
                .countByCommunityIdAndCreatedAtGreaterThanEqual(
                        communityId, LocalDateTime.now().minusDays(30));
        return new Aggregates(activeLoans, overdue, disputedLoans, pendingMembers, activeMembers, openFlags,
                completedLoans, onTime, returnDisputes, completedLends, returnDisputesReceived, volume30d);
    }

    private List<RecentActivityDto> recentActivity(Long communityId) {
        List<TransactionEvent> transactionEvents = transactionEventRepository
                .findRecentByCommunityId(communityId, PageRequest.of(0, RECENT_ACTIVITY_LIMIT));
        List<ReputationEvent> reputationEvents = reputationEventRepository
                .findTop20ByCommunityIdOrderByOccurredAtDesc(communityId);

        List<RecentActivityDto> merged = new ArrayList<>(transactionEvents.size() + reputationEvents.size());
        for (TransactionEvent event : transactionEvents) {
            merged.add(new RecentActivityDto(
                    event.getId(),
                    "transaction",
                    event.getEventType().name(),
                    event.getTransaction() != null ? event.getTransaction().getId() : null,
                    event.getActor() != null ? event.getActor().getId() : null,
                    event.getActor() != null ? event.getActor().getFullName() : null,
                    event.getCreatedAt()
            ));
        }
        for (ReputationEvent event : reputationEvents) {
            merged.add(new RecentActivityDto(
                    event.getId(),
                    "reputation",
                    event.getEventType().name(),
                    event.getTransaction() != null ? event.getTransaction().getId() : null,
                    event.getUser() != null ? event.getUser().getId() : null,
                    event.getUser() != null ? event.getUser().getFullName() : null,
                    event.getOccurredAt()
            ));
        }
        merged.sort(Comparator.comparing(RecentActivityDto::occurredAt).reversed());
        return merged.stream().limit(RECENT_ACTIVITY_LIMIT).toList();
    }

    private List<FlagResponse> recentFlags(Long communityId) {
        List<Flag> flags = flagRepository.findTop10ByCommunityIdOrderByOccurredAtDescIdDesc(communityId);
        return flags.stream().map(FlagResponse::from).toList();
    }

    private Integer rate(int numerator, int denominator) {
        return denominator <= 0 ? null : Math.round(numerator * 100f / denominator);
    }

    private void requireManager(Long communityId, User manager) {
        if (communityId == null || manager == null
                || !membershipService.isActiveManager(manager.getId(), communityId)) {
            throw new UnauthorizedException(
                    "Only an active manager can view community health");
        }
    }

    private record Aggregates(
            int activeLoans,
            int overdue,
            int disputedLoans,
            int pendingMembers,
            int activeMembers,
            int openFlags,
            int completedLoans,
            int onTime,
            int returnDisputes,
            int completedLends,
            int returnDisputesReceived,
            int volume30d
    ) {
    }
}