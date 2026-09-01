package com.borrowbox.dto;

import com.borrowbox.entity.CommunityAdmissionMode;
import com.borrowbox.entity.CommunityStatus;
import com.borrowbox.entity.CommunityType;

public record CommunityResponse(
        Long id,
        String name,
        String description,
        CommunityType type,
        CommunityStatus status,
        CommunityAdmissionMode admissionMode,
        int membershipCount,
        boolean isManager
) {
}
