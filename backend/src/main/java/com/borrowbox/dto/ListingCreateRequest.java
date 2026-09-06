package com.borrowbox.dto;

import jakarta.validation.constraints.NotNull;

public record ListingCreateRequest(
        @NotNull Long communityId
) {
}