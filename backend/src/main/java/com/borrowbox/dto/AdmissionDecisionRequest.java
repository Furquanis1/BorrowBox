package com.borrowbox.dto;

import com.borrowbox.entity.AdmissionDecision;
import jakarta.validation.constraints.NotNull;

public record AdmissionDecisionRequest(
        @NotNull AdmissionDecision decision
) {
}