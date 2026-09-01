package com.borrowbox.dto;

import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.MembershipVerificationMethod;

import java.time.LocalDateTime;
import java.util.Map;

public record MembershipResponse(
        Long id,
        Long userId,
        Long communityId,
        String userFullName,
        String communityName,
        MembershipRole role,
        MembershipStatus status,
        MembershipVerificationMethod verificationMethod,
        LocalDateTime verifiedAt,
        LocalDateTime joinedAt,
        Map<String, Object> contextMetadata
) {
}
