package com.borrowbox.dto;

import com.borrowbox.entity.CommunityAdmissionMode;
import com.borrowbox.entity.CommunityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CommunityCreateRequest(
        @NotBlank String name,
        String description,
        @NotNull CommunityType type,
        CommunityAdmissionMode admissionMode,
        BigDecimal locationLatitude,
        BigDecimal locationLongitude,
        Integer locationRadiusM
) {
}
