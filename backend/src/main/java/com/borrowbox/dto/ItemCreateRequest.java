package com.borrowbox.dto;

import jakarta.validation.constraints.NotBlank;

public record ItemCreateRequest(
        @NotBlank(message = "Title is required") String title,
        String description
) {
}
