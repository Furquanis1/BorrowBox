package com.borrowbox.repository;

import com.borrowbox.entity.CommunityRule;
import com.borrowbox.entity.CommunityRuleType;
import com.borrowbox.entity.CommunityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityRuleRepository extends JpaRepository<CommunityRule, Long> {

    List<CommunityRule> findByCommunityId(Long communityId);

    List<CommunityRule> findByCommunityIdAndStatus(Long communityId, CommunityStatus status);

    List<CommunityRule> findByCommunityIdAndRuleTypeAndStatus(
            Long communityId, CommunityRuleType ruleType, CommunityStatus status);
}