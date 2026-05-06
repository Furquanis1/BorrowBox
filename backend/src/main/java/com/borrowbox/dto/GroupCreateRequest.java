package com.borrowbox.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record GroupCreateRequest(
        @NotBlank(message = "Name is required") String name,
        String description,
        Set<Long> memberIds
) {
}
